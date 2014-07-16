package net.minecraft.hopper;

public class Report {
	private int id;
	private boolean published;
	private String token;
	
	public int getId() {
		return id;
	}
	
	public boolean isPublished() {
		return published;
	}
	
	public String getToken() {
		return token;
	}
	
	public boolean canBePublished() {
		return getToken() != null;
	}
}
