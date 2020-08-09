package hudson.plugins.octopusdeploy.services;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface OctoCliService {
    @NotNull
    Result launchOcto(FilePath workspace, Launcher launcher, List<String> commands, Boolean[] masks, EnvVars environment, BuildListener listener, String octoToolId);
}
