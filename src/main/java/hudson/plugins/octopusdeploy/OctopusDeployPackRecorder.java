package hudson.plugins.octopusdeploy;

import com.google.common.base.Splitter;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class OctopusDeployPackRecorder extends AbstractOctopusDeployRecorderBuildStep implements Serializable {

    private final String packageId;
    public String getPackageId() { return packageId; }

    private final String packageVersion;
    public String getPackageVersion() { return packageVersion; }

    private final String packageFormat;
    public String getPackageFormat() { return packageFormat; }

    public boolean isZipPackageFormat() { return "zip".equals(packageFormat); }
    public boolean isNuGetPackageFormat() { return "nuget".equals(packageFormat); }

    private final String sourcePath;
    public String getSourcePath() { return sourcePath; }

    private final String includePaths;
    public String getIncludePaths() { return includePaths; }

    private final String outputPath;
    public String getOutputPath() { return outputPath; }

    private final Boolean overwriteExisting;
    public Boolean getOverwriteExisting() { return overwriteExisting; }

    @DataBoundSetter
    public void setAdditionalArgs(String addtionalArgs) {
        this.additionalArgs = addtionalArgs == null ? null : addtionalArgs.trim();
    }

    public String getAdditionalArgs() {
        return this.additionalArgs;
    }

    @DataBoundConstructor
    public OctopusDeployPackRecorder(String toolId, String packageId, String packageVersion, String packageFormat,
                                     String sourcePath, String includePaths, String outputPath,
                                     Boolean overwriteExisting, Boolean verboseLogging) {
        this.toolId = toolId.trim();
        this.packageId = packageId.trim();
        this.packageVersion = packageVersion.trim();
        this.packageFormat = packageFormat.trim();
        this.sourcePath = sourcePath.trim();
        this.includePaths = includePaths.trim();
        this.outputPath = outputPath.trim();
        this.overwriteExisting = overwriteExisting;
        this.verboseLogging = verboseLogging;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {
        boolean success = true;
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);

        Log log = new Log(listenerAdapter);
        if (Result.FAILURE.equals(run.getResult())) {
            log.info("Not packaging the application due to job being in FAILED state.");
            return;
        }

        EnvVars envVars;
        try {
            envVars = run.getEnvironment(listener);
        } catch (Exception ex) {
            log.fatal(String.format("Failed to retrieve environment variables for this build '%s' - '%s'",
                    run.getParent().getName(), ex.getMessage()));
            run.setResult(Result.FAILURE);
            return;
        }
        VariableResolver resolver =  new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        //logStartHeader

        final List<String> commands = buildCommands(envInjector);

        try {
            final Boolean[] masks = getMasks(commands, OctoConstants.Commands.Arguments.MaskedArguments);

            Result result = launchOcto(workspace, launcher, commands, masks, envVars, listenerAdapter);
            success = result.equals(Result.SUCCESS);
        } catch (Exception ex) {
            log.fatal("Failed to package application: " + ex.getMessage());
            success = false;
        }

        if (!success) {
            run.setResult(Result.FAILURE);
        }
    }

    private List<String> buildCommands(final EnvironmentVariableValueInjector envInjector) {
        final List<String> commands = new ArrayList<>();
        String packageId = envInjector.injectEnvironmentVariableValues(this.packageId);
        String packageVersion = envInjector.injectEnvironmentVariableValues(this.packageVersion);
        String packageFormat = envInjector.injectEnvironmentVariableValues(this.packageFormat);
        String sourcePath = envInjector.injectEnvironmentVariableValues(this.sourcePath);
        String includePaths = envInjector.injectEnvironmentVariableValues(this.includePaths);
        String outputPath = envInjector.injectEnvironmentVariableValues(this.outputPath);
        Boolean overwriteExisting = this.overwriteExisting;
        Boolean verboseLogging = this.verboseLogging;
        String additionalArgs = envInjector.injectEnvironmentVariableValues(this.additionalArgs);

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

    @Extension
    @Symbol("octopusPack")
    public static final class DescriptorImpl extends AbstractOctopusDeployDescriptorImplStep {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Octopus Deploy: Package application";
        }

        /**
         * Check that the source path is valid and a directory.
         * @param sourcePath The deployment timeout (TimeSpan).
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckSourcePath(@QueryParameter String sourcePath) {
            return OctopusValidator.validateDirectory(sourcePath);
        }

        /**
         * Check that the output path is valid and a directory.
         * @param outputPath The deployment timeout (TimeSpan).
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckOutputPath(@QueryParameter String outputPath) {
            return OctopusValidator.validateDirectory(outputPath);
        }
    }
}
