package de.spassglas.vitrine.platform;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript-Laufzeit (Mozilla Rhino, MPL 2.0, ES6-Sprachstand). Sandbox: nur
 * die sicheren Standardobjekte, kein Zugriff auf Java-Klassen (ClassShutter
 * sperrt alles), Interpreter-Modus mit Anweisungszaehler fuer das Zeitlimit.
 */
final class JsScriptRuntime implements ScriptRuntime {

	private static final class Factory extends ContextFactory {
		@Override
		protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setLanguageVersion(Context.VERSION_ES6);
			cx.setOptimizationLevel(-1);
			cx.setInstructionObserverThreshold(5000);
			cx.setClassShutter(name -> false);
			return cx;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			Object deadline = cx.getThreadLocal("vitrine.deadline");
			if (deadline instanceof Long d && System.currentTimeMillis() > d) {
				throw new Error("Zeitlimit ueberschritten (Endlosschleife?)");
			}
		}
	}

	private static final Factory FACTORY = new Factory();

	private final ScriptableObject scope;
	private int depth;

	JsScriptRuntime() {
		Context cx = FACTORY.enterContext();
		try {
			scope = cx.initSafeStandardObjects();
		} finally {
			Context.exit();
		}
	}

	@Override
	public void start(String fileName, String source, Map<String, Object> api) {
		withLimit(START_LIMIT_MS, cx -> {
			ScriptableObject.putProperty(scope, "vitrine", toJs(cx, api));
			cx.evaluateString(scope, source, fileName, 1, null);
			return null;
		});
	}

	@Override
	public Object run(String fileName, String source) {
		return withLimit(CALL_LIMIT_MS * 4, cx -> fromJs(cx.evaluateString(scope, source, fileName, 1, null)));
	}

	private interface Body<T> {
		T run(Context cx);
	}

	private <T> T withLimit(long ms, Body<T> body) {
		Context cx = FACTORY.enterContext();
		boolean outer = depth++ == 0;
		Object previous = cx.getThreadLocal("vitrine.deadline");
		if (outer) {
			cx.putThreadLocal("vitrine.deadline", System.currentTimeMillis() + ms);
		}
		try {
			return body.run(cx);
		} catch (Error e) {
			// Zeitlimit aus observeInstructionCount als normale Ausnahme weitergeben
			throw new RuntimeException(e.getMessage());
		} finally {
			depth--;
			if (outer) {
				if (previous != null) {
					cx.putThreadLocal("vitrine.deadline", previous);
				} else {
					cx.removeThreadLocal("vitrine.deadline");
				}
			}
			Context.exit();
		}
	}

	@Override
	public void close() {
		ScriptableObject.deleteProperty(scope, "vitrine");
	}

	// --- Werte uebersetzen -------------------------------------------------------

	Object toJs(Context cx, Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof JsCallback c) {
			return c.fn;
		}
		if (o instanceof Boolean || o instanceof String) {
			return o;
		}
		if (o instanceof Number n) {
			return n.doubleValue();
		}
		if (o instanceof List<?> list) {
			Object[] items = new Object[list.size()];
			for (int i = 0; i < items.length; i++) {
				items[i] = toJs(cx, list.get(i));
			}
			return cx.newArray(scope, items);
		}
		if (o instanceof Map<?, ?> map) {
			Scriptable obj = cx.newObject(scope);
			for (Map.Entry<?, ?> e : map.entrySet()) {
				ScriptableObject.putProperty(obj, String.valueOf(e.getKey()), toJs(cx, e.getValue()));
			}
			return obj;
		}
		if (o instanceof ApiFunction f) {
			return new BaseFunction() {
				@Override
				public Object call(Context c, Scriptable s, Scriptable thisObj, Object[] args) {
					List<Object> in = new ArrayList<>();
					for (Object a : args) {
						in.add(fromJs(a));
					}
					return toJs(c, f.call(in));
				}
			};
		}
		if (o instanceof Callback cb) {
			return new BaseFunction() {
				@Override
				public Object call(Context c, Scriptable s, Scriptable thisObj, Object[] args) {
					Object[] in = new Object[args.length];
					for (int i = 0; i < args.length; i++) {
						in[i] = fromJs(args[i]);
					}
					return toJs(c, cb.call(in));
				}
			};
		}
		return String.valueOf(o);
	}

	Object fromJs(Object v) {
		if (v == null || v instanceof Undefined) {
			return null;
		}
		if (v instanceof Boolean) {
			return v;
		}
		if (v instanceof Number n) {
			return n.doubleValue();
		}
		if (v instanceof CharSequence cs) {
			return cs.toString();
		}
		if (v instanceof Function f) {
			return new JsCallback(f);
		}
		if (v instanceof NativeArray arr) {
			List<Object> list = new ArrayList<>();
			for (int i = 0; i < arr.getLength(); i++) {
				list.add(fromJs(arr.get(i, arr)));
			}
			return list;
		}
		if (v instanceof NativeObject obj) {
			Map<String, Object> map = new LinkedHashMap<>();
			for (Object id : obj.getIds()) {
				map.put(String.valueOf(id), fromJs(obj.get(String.valueOf(id), obj)));
			}
			return map;
		}
		return String.valueOf(v);
	}

	/** Eine JavaScript-Funktion als Vitrine-Callback, mit Zeitlimit je Aufruf. */
	final class JsCallback implements Callback {
		final Function fn;

		JsCallback(Function fn) {
			this.fn = fn;
		}

		@Override
		public Object call(Object... args) {
			return withLimit(CALL_LIMIT_MS, cx -> {
				Object[] in = new Object[args.length];
				for (int i = 0; i < args.length; i++) {
					in[i] = toJs(cx, args[i]);
				}
				return fromJs(fn.call(cx, scope, scope, in));
			});
		}
	}
}
