package net.minecraft.hopper;

import java.util.Map;

@SuppressWarnings("unused")
public class SubmitRequest {// TODO
	private String report;
	private String version;
	private String product;
	private Map<String, String> environment;
	
	public SubmitRequest(final String report, final String product, final String version, final Map<String, String> environment) {
		super();
		this.report = report;
		this.version = version;
		this.product = product;
		this.environment = environment;
	}
}
