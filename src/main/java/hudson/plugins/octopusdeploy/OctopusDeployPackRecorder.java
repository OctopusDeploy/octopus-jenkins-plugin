package hudson.plugins.octopusdeploy;

import com.google.common.base.Splitter;
import hudson.*;
import hudson.model.*;
import hudson.plugins.octopusdeploy.cli.LegacyCliWrapper;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

public class OctopusDeployPackRecorder extends AbstractOctopusDeployRecorderBuildStep implements Serializable {

    private final String packageId;
    public String getPackageId() { return packageId; }

    private String packageVersion;
    public String getPackageVersion() { return packageVersion; }

    private final String packageFormat;
    public String getPackageFormat() { return packageFormat; }

    public boolean isZipPackageFormat() { return "zip".equals(packageFormat); }
    public boolean isNuGetPackageFormat() { return "nuget".equals(packageFormat); }

    private final String sourcePath;
    public String getSourcePath() { return sourcePath; }

    private String includePaths;
    public String getIncludePaths() { return includePaths; }

    private String outputPath;
    public String getOutputPath() { return outputPath; }

    @DataBoundSetter
    public void setOutputPath(String outputPath) { this.outputPath = sanitizeValue(outputPath); }

    private Boolean overwriteExisting;
    public Boolean getOverwriteExisting() { return overwriteExisting; }

    @DataBoundSetter
    public void setPackageVersion(String packageVersion) { this.packageVersion = sanitizeValue(packageVersion); }

    @DataBoundSetter
    public void setIncludePaths(String includePaths) { this.includePaths = sanitizeValue(includePaths); }

    @DataBoundSetter
    public void setOverwriteExisting(Boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    @DataBoundConstructor
    public OctopusDeployPackRecorder(String toolId, String packageId, String packageFormat, String sourcePath) {
        this.toolId = sanitizeValue(toolId);
        this.packageId = sanitizeValue(packageId);
        this.packageFormat = sanitizeValue(packageFormat);
        this.sourcePath = sanitizeValue(sourcePath);

        this.outputPath = ".";
        this.includePaths = "**";
        this.overwriteExisting = false;
        this.verboseLogging = false;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws AbortException {
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
                    run.getParent().getName(), getExceptionMessage(ex)));
            run.setResult(Result.FAILURE);
            return;
        }
        VariableResolver resolver = new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        // Inject environment variables
        String packageId = envInjector.injectEnvironmentVariableValues(this.packageId);
        String packageVersion = envInjector.injectEnvironmentVariableValues(this.packageVersion);
        String packageFormat = envInjector.injectEnvironmentVariableValues(this.packageFormat);
        String sourcePath = envInjector.injectEnvironmentVariableValues(this.sourcePath);
        String includePathsValue = envInjector.injectEnvironmentVariableValues(this.includePaths);
        String outputPath = envInjector.injectEnvironmentVariableValues(this.outputPath);
        String additionalArgs = envInjector.injectEnvironmentVariableValues(this.additionalArgs);

        // Parse multi-line include paths
        List<String> includePathsList = null;
        if (StringUtils.isNotBlank(includePathsValue)) {
            includePathsList = Splitter.on("\n")
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(includePathsValue);
        }

        try {
            // Create wrapper
            LegacyCliWrapper wrapper = new LegacyCliWrapper.Builder(
                    getToolId(), workspace, launcher, envVars, listenerAdapter)
                    .verboseLogging(verboseLogging)
                    .build();

            // Execute pack command
            Result result = wrapper.pack(
                    packageId,
                    packageVersion,
                    packageFormat,
                    sourcePath,
                    includePathsList,
                    outputPath,
                    overwriteExisting != null && overwriteExisting,
                    additionalArgs
            );

            if (!result.equals(Result.SUCCESS)) {
                throw new AbortException("Failed to pack");
            }
        } catch (Exception ex) {
            log.fatal("Failed to package application: " + getExceptionMessage(ex));
            throw new AbortException("Failed to pack");
        }
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
