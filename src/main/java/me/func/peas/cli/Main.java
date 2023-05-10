package me.func.peas.cli;

import picocli.CommandLine;

public final class Main {
	private Main() {}

	public static void main(String... args) {
		var cmd = new CommandLine(new PeasCommand());
		cmd.execute(args);
	}
}
