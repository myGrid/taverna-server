package org.taverna.server.master.scape.beanshells.utils;

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A caching RE engine so that REs aren't recompiled so often. The cache is 
 * @author Donal Fellows
 */
public class RECache {
	static WeakHashMap<String, SoftReference<Pattern>> cache = new WeakHashMap<>();

	private RECache() {
	}

	public static Pattern regexp(String regexp) {
		synchronized (cache) {
			SoftReference<Pattern> ref = cache.get(regexp);
			Pattern p = (ref == null ? null : ref.get());
			if (p == null) {
				p = Pattern.compile(regexp);
				cache.put(regexp, new SoftReference<>(p));
			}
			return p;
		}
	}

	public static Matcher regexp(String regexp, String toMatchAgainst) {
		return regexp(regexp).matcher(toMatchAgainst);
	}

	public static boolean match(String regexp, String toMatchAgainst) {
		return regexp(regexp).matcher(toMatchAgainst).matches();
	}
}
