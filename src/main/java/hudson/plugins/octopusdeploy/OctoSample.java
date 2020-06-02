package hudson.plugins.octopusdeploy;

import com.google.inject.Inject;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.QueryParameter;

/**
 * This plugin can be added as a buildstep to any pipeline.
 * @author Matthias Sommer
 */
public class OctoSample extends Builder implements SimpleBuildStep, Serializable {

    private String project;
    private String project2;

    @Inject
    @DataBoundConstructor
    public OctoSample(String project, String project2) {
        this.project = project;
        this.project2 = project2;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    public String getProject(){
        return this.project;
    }

    @DataBoundSetter
    public void setProject2(String project2) {
        this.project2 = project2;
    }

    public String getProject2(){
        return this.project2;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) {


        listener.getLogger().println("Huego " + project + " : " + project2);
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * This allows to add this plugin as a build step to a pipeline. Is
     * reflected by config.jelly for the view.
     */
    @Extension
    @Symbol("octosample")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

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
            return "Octo Sample";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            save();
            return super.configure(req, formData);
        }
    }
}

