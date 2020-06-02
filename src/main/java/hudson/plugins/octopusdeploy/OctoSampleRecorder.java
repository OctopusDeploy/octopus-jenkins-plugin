package hudson.plugins.octopusdeploy;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.Serializable;

public class OctoSampleRecorder extends Recorder implements SimpleBuildStep, Serializable {

    private String project;
    private String project2;

    @Inject
    @DataBoundConstructor
    public OctoSampleRecorder(String project, String project2) {
        this.project = project;
        this.project2 = project2;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {


        listener.getLogger().println("Huego " + project + " : " + project2);
    }


    /**
     * This allows to add this plugin as a build step to a pipeline. Is
     * reflected by config.jelly for the view.
     */
    @Extension
    @Symbol("octosamplerecorder")
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        /**
         * load the persisted global configuration.
         */
        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // We are always OK with someone adding this as a build step for their job
            return true;
        }

        /**
         * What you see as the plugin name in the config view.
         *
         * @return if configuration was successful
         */
        @Override
        public String getDisplayName() {
            return "Octo Sample Recorder";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            save();
            return super.configure(req, formData);
        }
    }
}