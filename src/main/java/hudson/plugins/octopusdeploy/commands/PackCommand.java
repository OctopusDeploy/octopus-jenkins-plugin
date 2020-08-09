package hudson.plugins.octopusdeploy.commands;

import com.google.common.base.Splitter;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
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

public class PackCommand extends AbstractOctoCommand {
    private final PackCommandParameters parameters;

    public PackCommand(PackCommandParameters packCommandParameters) {
        this.parameters = packCommandParameters;
    }

    public Result perform(@Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener, @Nonnull EnvVars envVars) {
        boolean success = true;
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);
        Log log = new Log(listenerAdapter);

        VariableResolver resolver =  new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        //logStartHeader

        final List<String> commands = buildCommands(envInjector);

        try {
            final Boolean[] masks = getMasks(commands, OctoConstants.Commands.Arguments.MaskedArguments);

            Result result = this.parameters.getOctoCliService().launchOcto(workspace, launcher, commands, masks, envVars, listenerAdapter, this.parameters.getToolId());
            success = result.equals(Result.SUCCESS);
        } catch (Exception ex) {
            log.fatal("Failed to package application: " + ex.getMessage());
            success = false;
        }

        if (!success) {
            return Result.FAILURE;
        }

        return Result.SUCCESS;
    }

    private List<String> buildCommands(final EnvironmentVariableValueInjector envInjector) {
        final List<String> commands = new ArrayList<>();
        String packageId = envInjector.injectEnvironmentVariableValues(this.parameters.getPackageId());
        String packageVersion = envInjector.injectEnvironmentVariableValues(this.parameters.getPackageVersion());
        String packageFormat = envInjector.injectEnvironmentVariableValues(this.parameters.getPackageFormat());
        String sourcePath = envInjector.injectEnvironmentVariableValues(this.parameters.getSourcePath());
        String includePaths = envInjector.injectEnvironmentVariableValues(this.parameters.getIncludePaths());
        String outputPath = envInjector.injectEnvironmentVariableValues(this.parameters.getOutputPath());
        Boolean overwriteExisting = this.parameters.isOverwriteExisting();
        Boolean verboseLogging = this.parameters.isVerboseLogging();
        String additionalArgs = envInjector.injectEnvironmentVariableValues(this.parameters.getAdditionalArgs());

        checkState(StringUtils.isNotBlank(packageId), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Package ID"));

        commands.add("pack");

        commands.add("--id");
        commands.add(packageId);

        if (StringUtils.isNotBlank(packageVersion)) {
            commands.add("--version");
            commands.add(packageVersion);
        }

        if (StringUtils.isNotBlank(packageFormat)) {
            commands.add("--format");
            commands.add(packageFormat);
        }

        if (StringUtils.isNotBlank(sourcePath)) {
            commands.add("--basePath");
            commands.add(sourcePath);
        }

        if (StringUtils.isNotBlank(includePaths)) {
            final Iterable<String> includePathsSplit = Splitter.on("\n")
                    .trimResults()
                    .omitEmptyStrings()
                    .split(includePaths);
            for (final String include : includePathsSplit) {
                commands.add("--include");
                commands.add(include);
            }
        }

        if (StringUtils.isNotBlank(outputPath)) {
            commands.add("--outFolder");
            commands.add(outputPath);
        }

        if (overwriteExisting) {
            commands.add("--overwrite");
        }

        if (verboseLogging) {
            commands.add("--verbose");
        }

        if(StringUtils.isNotBlank(additionalArgs)) {
            final String[] myArgs = Commandline.translateCommandline(additionalArgs);
            commands.addAll(Arrays.asList(myArgs));
        }

        return commands;
    }

    public boolean isZipPackageFormat() { return "zip".equals(this.parameters.getPackageFormat()); }
    public boolean isNuGetPackageFormat() { return "nuget".equals(this.parameters.getPackageFormat()); }
}
