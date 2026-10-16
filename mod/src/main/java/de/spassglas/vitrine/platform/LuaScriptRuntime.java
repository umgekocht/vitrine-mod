package de.spassglas.vitrine.platform;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lua-Laufzeit (LuaJ, MIT). Sandbox: nur Basis-, String-, Tabellen-, Mathe- und
 * Bit-Bibliothek; kein {@code io}, {@code os}, {@code luajava}, keine Coroutinen,
 * kein Laden fremder Dateien. Jeder Aufruf hat ein Zeitlimit (Zaehl-Hook alle
 * 1000 Anweisungen), damit eine Endlosschleife das Spiel nicht einfriert.
 */
final class LuaScriptRuntime implements ScriptRuntime {

	private final Globals globals = new Globals();
	private volatile long deadline = Long.MAX_VALUE;
	private int depth;

	LuaScriptRuntime() {
		globals.load(new JseBaseLib());
		globals.load(new PackageLib());
		globals.load(new Bit32Lib());
		globals.load(new TableLib());
		globals.load(new StringLib());
		globals.load(new JseMathLib());
		globals.load(new DebugLib());
		LoadState.install(globals);
		LuaC.install(globals);
		// Zeitlimit-Hook setzen, danach die Debug-Bibliothek fuer Skripte entfernen
		LuaValue hook = new VarArgFunction() {
			@Override
			public Varargs invoke(Varargs args) {
				if (System.currentTimeMillis() > deadline) {
					throw new LuaError("Zeitlimit ueberschritten (Endlosschleife?)");
				}
				return NONE;
			}
		};
		globals.get("debug").get("sethook").call(hook, LuaValue.valueOf(""), LuaValue.valueOf(1000));
		for (String unsafe : new String[] {"debug", "dofile", "loadfile", "require", "package", "collectgarbage"}) {
			globals.set(unsafe, LuaValue.NIL);
		}
	}

	@Override
	public void start(String fileName, String source, Map<String, Object> api) {
		globals.set("vitrine", toLua(api));
		withLimit(START_LIMIT_MS, () -> globals.load(source, "@" + fileName).call());
	}

	@Override
	public Object run(String fileName, String source) {
		return withLimit(CALL_LIMIT_MS * 4, () -> fromLua(globals.load(source, "@" + fileName).call()));
	}

	private <T> T withLimit(long ms, java.util.function.Supplier<T> body) {
		boolean outer = depth++ == 0;
		if (outer) {
			deadline = System.currentTimeMillis() + ms;
		}
		try {
			return body.get();
		} finally {
			if (--depth == 0) {
				deadline = Long.MAX_VALUE;
			}
		}
	}

	@Override
	public void close() {
		globals.set("vitrine", LuaValue.NIL);
	}

	// --- Werte uebersetzen -------------------------------------------------------

	LuaValue toLua(Object o) {
		if (o == null) {
			return LuaValue.NIL;
		}
		if (o instanceof LuaCallback c) {
			return c.fn;
		}
		if (o instanceof Boolean b) {
			return LuaValue.valueOf(b);
		}
		if (o instanceof Number n) {
			double d = n.doubleValue();
			return d == Math.rint(d) && Math.abs(d) < 1e15 ? LuaValue.valueOf((long) d) : LuaValue.valueOf(d);
		}
		if (o instanceof String s) {
			return LuaValue.valueOf(s);
		}
		if (o instanceof List<?> list) {
			LuaTable t = new LuaTable();
			for (int i = 0; i < list.size(); i++) {
				t.set(i + 1, toLua(list.get(i)));
			}
			return t;
		}
		if (o instanceof Map<?, ?> map) {
			LuaTable t = new LuaTable();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				t.set(String.valueOf(e.getKey()), toLua(e.getValue()));
			}
			return t;
		}
		if (o instanceof ApiFunction f) {
			return new VarArgFunction() {
				@Override
				public Varargs invoke(Varargs args) {
					List<Object> in = new ArrayList<>();
					for (int i = 1; i <= args.narg(); i++) {
						in.add(fromLua(args.arg(i)));
					}
					try {
						return toLua(f.call(in));
					} catch (LuaError e) {
						throw e;
					} catch (RuntimeException e) {
						throw new LuaError(e.getMessage() == null ? e.toString() : e.getMessage());
					}
				}
			};
		}
		if (o instanceof Callback c) {
			return new VarArgFunction() {
				@Override
				public Varargs invoke(Varargs args) {
					Object[] in = new Object[args.narg()];
					for (int i = 0; i < in.length; i++) {
						in[i] = fromLua(args.arg(i + 1));
					}
					return toLua(c.call(in));
				}
			};
		}
		return LuaValue.valueOf(String.valueOf(o));
	}

	Object fromLua(LuaValue v) {
		if (v == null || v.isnil()) {
			return null;
		}
		if (v.isboolean()) {
			return v.toboolean();
		}
		if (v.isnumber() && !v.isstring()) {
			return v.todouble();
		}
		if (v.type() == LuaValue.TNUMBER) {
			return v.todouble();
		}
		if (v.isstring()) {
			return v.tojstring();
		}
		if (v.isfunction()) {
			return new LuaCallback((LuaFunction) v.checkfunction());
		}
		if (v.istable()) {
			LuaTable t = (LuaTable) v;
			int n = t.length();
			int keys = 0;
			LuaValue k = LuaValue.NIL;
			while (true) {
				Varargs next = t.next(k);
				if ((k = next.arg1()).isnil()) {
					break;
				}
				keys++;
			}
			if (n > 0 && n == keys) {
				List<Object> list = new ArrayList<>(n);
				for (int i = 1; i <= n; i++) {
					list.add(fromLua(t.get(i)));
				}
				return list;
			}
			Map<String, Object> map = new LinkedHashMap<>();
			k = LuaValue.NIL;
			while (true) {
				Varargs next = t.next(k);
				if ((k = next.arg1()).isnil()) {
					break;
				}
				map.put(k.tojstring(), fromLua(next.arg(2)));
			}
			return map;
		}
		return v.tojstring();
	}

	/** Eine Lua-Funktion als Vitrine-Callback, mit Zeitlimit je Aufruf. */
	final class LuaCallback implements Callback {
		final LuaFunction fn;

		LuaCallback(LuaFunction fn) {
			this.fn = fn;
		}

		@Override
		public Object call(Object... args) {
			return withLimit(CALL_LIMIT_MS, () -> {
				LuaValue[] in = new LuaValue[args.length];
				for (int i = 0; i < args.length; i++) {
					in[i] = toLua(args[i]);
				}
				return fromLua(fn.invoke(LuaValue.varargsOf(in)).arg1());
			});
		}
	}
}
