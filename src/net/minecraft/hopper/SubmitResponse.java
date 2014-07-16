package net.minecraft.hopper;

public class SubmitResponse extends Response {
	private Report report;
	private Crash crash;
	private Problem problem;
	
	public Report getReport() {
		return report;
	}
	
	public Crash getCrash() {
		return crash;
	}
	
	public Problem getProblem() {
		return problem;
	}
}
