package hudson.plugins.octopusdeploy.commands;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.Log;

import javax.annotation.Nonnull;

public interface OctoCommand {
    Result perform(@Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull EnvVars envVars);
}
