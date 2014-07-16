package net.minecraft.hopper;

@SuppressWarnings("unused")
public class PublishRequest {// TODO
	private String token;
	private int report_id;
	
	public PublishRequest(final Report report) {
		super();
		report_id = report.getId();
		token = report.getToken();
	}
}
