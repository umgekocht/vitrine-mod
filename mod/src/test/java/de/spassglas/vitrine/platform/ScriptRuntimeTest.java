package de.spassglas.vitrine.platform;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/** Prueft beide Laufzeiten gleich: API-Aufrufe, Rueckrufe, Sandbox, Zeitlimit. */
public class ScriptRuntimeTest {

	@DataProvider
	public Object[][] runtimes() {
		return new Object[][] {{"lua"}, {"js"}};
	}

	private static ScriptRuntime make(String kind) {
		return kind.equals("lua") ? new LuaScriptRuntime() : new JsScriptRuntime();
	}

	private static String src(String kind, String lua, String js) {
		return kind.equals("lua") ? lua : js;
	}

	@Test(dataProvider = "runtimes")
	public void callsApiAndStoresCallbacks(String kind) throws Exception {
		List<Object> logged = new ArrayList<>();
		List<Callback> handlers = new ArrayList<>();
		Map<String, Object> api = new LinkedHashMap<>();
		api.put("log", (ApiFunction) a -> {
			logged.addAll(a);
			return null;
		});
		api.put("on", (ApiFunction) a -> {
			handlers.add((Callback) a.get(1));
			return null;
		});
		api.put("player", (ApiFunction) a -> Map.of("name", "umgekocht", "health", 20.0));
		try (ScriptRuntime rt = make(kind)) {
			rt.start("main", src(kind,
					"local p = vitrine.player()\nvitrine.log(p.name, p.health)\n"
							+ "vitrine.on('tick', function(n) vitrine.log('tick', n + 1) return n * 2 end)",
					"var p = vitrine.player();\nvitrine.log(p.name, p.health);\n"
							+ "vitrine.on('tick', function(n) { vitrine.log('tick', n + 1); return n * 2; });"), api);
			assertEquals(logged.get(0), "umgekocht");
			assertEquals(((Number) logged.get(1)).doubleValue(), 20.0);
			assertEquals(handlers.size(), 1);
			Object r = handlers.get(0).call(4.0);
			assertEquals(((Number) r).doubleValue(), 8.0);
			assertEquals(logged.get(2), "tick");
			assertEquals(((Number) logged.get(3)).doubleValue(), 5.0);
		}
	}

	@Test(dataProvider = "runtimes")
	public void convertsTablesAndArrays(String kind) throws Exception {
		Object[] got = new Object[1];
		Map<String, Object> api = new LinkedHashMap<>();
		api.put("take", (ApiFunction) a -> {
			got[0] = a.get(0);
			return null;
		});
		try (ScriptRuntime rt = make(kind)) {
			rt.start("main", src(kind, "vitrine.take({a = 1, list = {'x', 'y'}})",
					"vitrine.take({a: 1, list: ['x', 'y']});"), api);
		}
		Map<?, ?> m = (Map<?, ?>) got[0];
		assertEquals(((Number) m.get("a")).doubleValue(), 1.0);
		assertEquals(m.get("list"), List.of("x", "y"));
	}

	@Test(dataProvider = "runtimes")
	public void stopsEndlessLoops(String kind) {
		ScriptRuntime rt = make(kind);
		long start = System.currentTimeMillis();
		assertThrows(Exception.class, () -> rt.start("main", src(kind, "while true do end", "while (true) {}"),
				new LinkedHashMap<>()));
		assertTrue(System.currentTimeMillis() - start < 5000, "Zeitlimit greift nicht");
		rt.close();
	}

	@Test
	public void luaHasNoFileOrOsAccess() throws Exception {
		Object[] got = new Object[1];
		Map<String, Object> api = new LinkedHashMap<>();
		api.put("take", (ApiFunction) a -> {
			got[0] = a.isEmpty() ? null : a.get(0);
			return null;
		});
		try (ScriptRuntime rt = make("lua")) {
			rt.start("main", "vitrine.take(tostring(io) .. tostring(os) .. tostring(luajava) .. tostring(require) .. tostring(debug))", api);
		}
		assertEquals(got[0], "nilnilnilnilnil");
	}

	@Test
	public void jsCannotReachJava() throws Exception {
		Object[] got = new Object[1];
		Map<String, Object> api = new LinkedHashMap<>();
		api.put("take", (ApiFunction) a -> {
			got[0] = a.get(0);
			return null;
		});
		try (ScriptRuntime rt = make("js")) {
			rt.start("main", "vitrine.take(typeof java + '/' + typeof Packages + '/' + typeof importClass);", api);
		}
		assertNotNull(got[0]);
		assertEquals(got[0], "undefined/undefined/undefined");
	}
}
