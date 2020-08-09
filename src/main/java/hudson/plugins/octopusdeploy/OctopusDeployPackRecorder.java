package hudson.plugins.octopusdeploy;

import com.google.inject.Guice;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.octopusdeploy.commands.PackCommand;
import hudson.plugins.octopusdeploy.commands.PackCommandParameters;
import hudson.plugins.octopusdeploy.services.ServiceModule;
import hudson.util.FormValidation;
import jenkins.util.BuildListenerAdapter;
import org.jenkinsci.Symbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.Serializable;

import static com.google.common.base.Preconditions.checkState;
import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

public class OctopusDeployPackRecorder extends AbstractOctopusDeployRecorderBuildStep implements Serializable {

    private final String packageId;
    public String getPackageId() { return packageId; }

    private String packageVersion;
    public String getPackageVersion() { return packageVersion; }

    private final String packageFormat;
    public String getPackageFormat() { return packageFormat; }

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
        Guice.createInjector(new ServiceModule()).injectMembers(this);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {
        Log log = getLog(listener);
        if (CheckForFailedTask(run, log)) return;

        EnvVars envVars = getEnvVars(run, listener, log);
        if (envVars == null) return;

        PackCommand command = new PackCommand(
                new PackCommandParameters(
                        this.getOctoCliService(),
                        this.getToolId(),
                        this.getPackageId(),
                        this.getPackageFormat(),
                        this.getSourcePath(),
                        this.getPackageVersion(),
                        this.getIncludePaths(),
                        this.outputPath,
                        this.additionalArgs,
                        this.verboseLogging,
                        this.overwriteExisting));
        Result result = command.perform(workspace, launcher, listener, envVars);
        run.setResult(result);
        return;
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
