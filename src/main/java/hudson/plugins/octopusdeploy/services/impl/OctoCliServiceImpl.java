package hudson.plugins.octopusdeploy.services.impl;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.Log;
import hudson.plugins.octopusdeploy.OctoInstallation;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.plugins.octopusdeploy.services.OctoCliService;
import jenkins.model.Jenkins;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class OctoCliServiceImpl implements OctoCliService {
    @Override
    public @NotNull Result launchOcto(FilePath workspace, Launcher launcher, List<String> commands, Boolean[] masks, EnvVars environment, BuildListener listener, String octoToolId) {
        Log log = new Log(listener);
        int exitCode = -1;
        final String octopusCli = octoToolId;

        checkState(StringUtils.isNotBlank(octopusCli), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Octopus CLI"));
        Node builtOn = workspace.toComputer().getNode();
        final String cliPath = getOctopusToolPath(octopusCli, builtOn, environment, launcher.getListener());
        if(StringUtils.isNotBlank(cliPath)) {
            final List<String> cmdArgs = new ArrayList<>();
            final List<Boolean> cmdMasks = new ArrayList<>();

            cmdArgs.add(cliPath);
            cmdArgs.addAll(commands);

            cmdMasks.add(Boolean.FALSE);
            cmdMasks.addAll(Arrays.asList(masks));

            Proc process = null;
            try {
                //environment.put("OCTOEXTENSION", getClass().getPackage().getImplementationVersion());
                environment.put("OCTOEXTENSION", "");
                process = launcher
                        .launch()
                        .cmds(cmdArgs)
                        .masks(ArrayUtils.toPrimitive(cmdMasks.toArray((Boolean[]) Array.newInstance(Boolean.class, 0))))
                        .stdout(listener)
                        .envs(environment)
                        .pwd(workspace)
                        .start();

                exitCode = process.join();

                log.info(String.format("Octopus CLI exit code: %d", exitCode));

            } catch (IOException e) {
                final String message = "Error from Octopus CLI: " + e.getMessage();
                log.error(message);
                return Result.FAILURE;
            } catch (InterruptedException e) {
                final String message = "Unable to wait for Octopus CLI: " + e.getMessage();
                log.error(message);
                return Result.FAILURE;
            }

            if(exitCode == 0)
                return Result.SUCCESS;

            log.error("Unable to create or deploy release. Please check the build log for details on the error.");
            return Result.FAILURE;
        }

        log.error("OCTOPUS-JENKINS-INPUT-ERROR-0003: The path of \"" + cliPath + "\" for the selected Octopus CLI does not exist.");
        return Result.FAILURE;
    }

    public static String getOctopusToolPath(String name, Node builtOn, EnvVars env, TaskListener taskListener) {
        OctoInstallation.DescriptorImpl descriptor = (OctoInstallation.DescriptorImpl) Jenkins.getInstance().getDescriptor(OctoInstallation.class);
        return descriptor.getInstallation(name).getPathToOctoExe(builtOn, env, taskListener);
    }
}
