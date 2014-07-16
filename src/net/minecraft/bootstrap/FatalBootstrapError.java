package net.minecraft.bootstrap;

public class FatalBootstrapError extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public FatalBootstrapError(final String reason) {
		super(reason);
	}
}
