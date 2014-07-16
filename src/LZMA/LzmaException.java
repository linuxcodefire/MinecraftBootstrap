package LZMA;

import java.io.IOException;

public class LzmaException extends IOException {
	private static final long serialVersionUID = 1L;
	
	public LzmaException() {
		super();
	}
	
	public LzmaException(final String s) {
		super(s);
	}
}
