package hudson.plugins.octopusdeploy.cli;

import hudson.model.Result;

/**
 * Holds the result of a CLI execution including stdout and exit code.
 */
public class CliExecutionResult {
    private final String stdout;
    private final int exitCode;

    public CliExecutionResult(String stdout, int exitCode) {
        this.stdout = stdout;
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public Result toResult() {
        return isSuccess() ? Result.SUCCESS : Result.FAILURE;
    }
}
