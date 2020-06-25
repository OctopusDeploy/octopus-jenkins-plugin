package hudson.plugins.octopusdeploy.commands;

import com.google.common.base.Splitter;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.EnvironmentVariableValueInjector;
import hudson.plugins.octopusdeploy.Log;
import hudson.plugins.octopusdeploy.OctopusDeployServer;
import hudson.plugins.octopusdeploy.OverwriteMode;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.util.VariableResolver;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class PushBuildInformationCommand extends AbstractOctoCommand {

    private final PushBuildInformationCommandParameters parameters;

    public PushBuildInformationCommand(PushBuildInformationCommandParameters pushBuildInformationCommandParameters) {
        this.parameters = pushBuildInformationCommandParameters;
    }

    @Override
    public Result perform(@Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull EnvVars envVars) {
        boolean success = true;
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);
        Log log = new Log(listenerAdapter);

        VariableResolver resolver =  new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        try {
            final List<String> commands = buildCommands(envInjector, workspace);
            final Boolean[] masks = getMasks(commands, OctoConstants.Commands.Arguments.MaskedArguments);
            Result result = this.parameters.getOctoCliService().launchOcto(workspace, launcher, commands, masks, envVars, listenerAdapter, this.parameters.getToolId());
            success = result.equals(Result.SUCCESS);
        } catch (Exception ex) {
            log.fatal("Failed to push the build information: " + ex.getMessage());
            success = false;
        }

        if (!success) {
            return Result.FAILURE;
        }

        return Result.SUCCESS;
    }

    private List<String> buildCommands(final EnvironmentVariableValueInjector envInjector, FilePath workspace) throws IOException, InterruptedException {
        final List<String> commands = new ArrayList<>();

        OctopusDeployServer server = getOctopusDeployServer(this.parameters.getServerId());
        String serverUrl = server.getUrl();
        String apiKey = server.getApiKey().getPlainText();
        boolean ignoreSslErrors = server.getIgnoreSslErrors();
        OverwriteMode overwriteMode = this.parameters.getOverwriteMode();
        String packageIds = envInjector.injectEnvironmentVariableValues(this.parameters.getPackageId());
        String additionalArgs = envInjector.injectEnvironmentVariableValues(this.parameters.getAdditionalArgs());

        checkState(StringUtils.isNotBlank(serverUrl), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Octopus URL"));
        checkState(StringUtils.isNotBlank(apiKey), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "API Key"));

        commands.add("build-information");

        commands.add("--server");
        commands.add(serverUrl);

        commands.add("--apiKey");
        commands.add(apiKey);

        if (StringUtils.isNotBlank(this.parameters.getSpaceId())) {
            commands.add("--space");
            commands.add(this.parameters.getSpaceId());
        }

        if (StringUtils.isNotBlank(packageIds)) {
            final Iterable<String> packageIdsSplit = Splitter.on("\n")
                    .trimResults()
                    .omitEmptyStrings()
                    .split(packageIds);
            for(final String packageId : packageIdsSplit) {
                commands.add("--package-id");
                commands.add(packageId);
            }
        }

        commands.add("--version");
        commands.add(this.parameters.getPackageVersion());

        final String buildInformationFile = this.parameters.getScmCommitService().getBuildInformationFromScm(envInjector, workspace, this.parameters.getGitUrl(), this.parameters.getGitCommit(), this.parameters.getCommentParser(), this.parameters.isVerboseLogging());
        commands.add("--file");
        commands.add(buildInformationFile);

        if (overwriteMode != OverwriteMode.FailIfExists) {
            commands.add("--overwrite-mode");
            commands.add(overwriteMode.name());
        }

        if (this.parameters.isVerboseLogging()) {
            commands.add("--debug");
        }

        if (ignoreSslErrors) {
            commands.add("--ignoreSslErrors");
        }

        if(StringUtils.isNotBlank(additionalArgs)) {
            final String[] myArgs = Commandline.translateCommandline(additionalArgs);
            commands.addAll(Arrays.asList(myArgs));
        }

        return commands;
    }
}