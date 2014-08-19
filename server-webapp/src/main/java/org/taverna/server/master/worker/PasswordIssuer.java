package org.taverna.server.master.worker;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A simple password issuing bean.
 * 
 * @author Donal Fellows
 */
public class PasswordIssuer {
	private final char[] alphabet;
	private static final char[] LARGE_ALPHABET = { 'a', 'b', 'c', 'd', 'e',
			'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r',
			's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E',
			'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R',
			'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '1', '2', '3', '4', '5',
			'6', '7', '8', '9', '0', '!', '@', '#', '$', '%', '^', '&', '*',
			'(', ')', ',', '.', '<', '>', '/', '?', ':', ';', '-', '_', '+',
			'[', ']', '{', '}', '`', '~' };
	private static final char[] SMALL_ALPHABET = {
		'a', 'b', 'c', 'd', 'e', 'f', 'g',
		'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
		'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
		'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
		'U', 'V', 'W', 'X', 'Y', 'Z', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', '0', '_'
	};
	private Log log = LogFactory.getLog("Taverna.Server.Worker");
	private SecureRandom r;
	private int length;
	public static final boolean RESTRICT_ALPHABET = true;
	private static char[] getAlphabet() {
		return RESTRICT_ALPHABET ? SMALL_ALPHABET : LARGE_ALPHABET;
	}

	public PasswordIssuer() {
		alphabet = getAlphabet();
		r = new SecureRandom();
		log.info("constructing passwords with " + r.getAlgorithm());
		setLength(8);
	}

	public PasswordIssuer(String algorithm) throws NoSuchAlgorithmException {
		alphabet = getAlphabet();
		r = SecureRandom.getInstance(algorithm);
		log.info("constructing passwords with " + r.getAlgorithm());
		setLength(8);
	}

	public void setLength(int length) {
		this.length = length;
		log.info("issued password will be " + this.length
				+ " symbols chosen from " + alphabet.length);
	}

	/**
	 * Issue a password.
	 * 
	 * @return The new password.
	 */
	public String issue() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++)
			sb.append(alphabet[r.nextInt(alphabet.length)]);
		log.info("issued new password of length " + sb.length());
		return sb.toString();
	}
}
