package hudson.plugins.octopusdeploy;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.octopusdeploy.api.OctopusApi;
import com.octopusdeploy.api.data.Space;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.*;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.plugins.octopusdeploy.services.OctoCliService;
import hudson.plugins.octopusdeploy.services.ServiceModule;
import hudson.plugins.octopusdeploy.utils.Lazy;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.BuildListenerAdapter;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

/**
 * The AbstractOctopusDeployRecorder tries to take care of most of the Octopus
 * Deploy server access.
 * @author wbenayed
 */
public abstract class AbstractOctopusDeployRecorderBuildStep extends Builder implements SimpleBuildStep {

    /**
     * Cache for OctopusDeployServer instance used in deployment
     * transient keyword prevents leaking API key to Job configuration
     */
    protected transient Lazy<OctopusDeployServer> lazyOctopusDeployServer;

    public OctopusDeployServer getOctopusDeployServer() {
        return lazyOctopusDeployServer
                .getOrCompute(()->getOctopusDeployServer(getServerId()));
    }

    /**
     * The serverId to use for this deployment
     */
    protected String serverId;
    public String getServerId() {
        return serverId;
    }

    /**
     * The toolId to use for this deployment
     */
    protected String toolId;
    public String getToolId() {return toolId;}

    /**
     * The spaceId to use for this deployment
     */
    protected String spaceId;
    public String getSpaceId() {
        return spaceId;
    }

    public static Boolean hasSpaces() {
        try {
            return getDefaultOctopusDeployServer().getApi().forSystem().getSupportsSpaces();
        } catch (Exception ex) {
            Logger.getLogger(AbstractOctopusDeployRecorderBuildStep.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    /**
     * The project name as defined in Octopus.
     */
    protected String project;
    public String getProject() {
        return project;
    }

    /**
     * The environment to deploy to, if we are deploying.
     */
    protected String environment;
    public String getEnvironment() {
        return environment;
    }

    /**
     * The variables to use for a deploy to in Octopus.
     */
    protected String variables;
    public String getVariables() {
        return variables;
    }

    /**
     * The Tenant to use for a deploy to in Octopus.
     */
    protected String tenant;
    public String getTenant() {
        return tenant;
    }

    protected String tenantTag;
    public String getTenantTag() {
        return tenantTag;
    }

    /**
     * The additional arguments to pass to Octopus CLI
     */
    protected String additionalArgs;
    public String getAdditionalArgs() {
        return additionalArgs;
    }

    @DataBoundSetter
    public void setAdditionalArgs(String additionalArgs) {
        this.additionalArgs = sanitizeValue(additionalArgs);
    }

    /**
     * Whether or not perform will return control immediately, or wait until the Deployment
     * task is completed.
     */
    protected boolean waitForDeployment;
    public boolean getWaitForDeployment() {
        return waitForDeployment;
    }

    /**
     * Whether or not to enable verbose logging
     */
    protected boolean verboseLogging;
    public boolean getVerboseLogging() {
        return verboseLogging;
    }

    @DataBoundSetter
    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    /**
     * Specifies maximum time (timespan format) that the console session will wait for
     * the deployment to finish(default 00:10:00)
     */
    protected String deploymentTimeout;
    public String getDeploymentTimeout() {
        return deploymentTimeout;
    }

    /**
     * Whether to cancel the deployment if the deployment timeout is reached
     */
    protected boolean cancelOnTimeout;
    public boolean getCancelOnTimeout() {
        return cancelOnTimeout;
    }

    /**
     * Get the default OctopusDeployServer from OctopusDeployPlugin configuration
     * @return the default server
     * */
    protected static OctopusDeployServer getDefaultOctopusDeployServer() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            throw new IllegalStateException("Jenkins instance is null");
        }
        OctopusDeployPlugin.DescriptorImpl descriptor = (OctopusDeployPlugin.DescriptorImpl) jenkinsInstance.getDescriptor(OctopusDeployPlugin.class);
        return descriptor.getDefaultOctopusDeployServer();
    }

    protected OctoCliService octoCliService;
    protected OctoCliService getOctoCliService() { return this.octoCliService; }

    @Inject
    protected void setOctoCliService(OctoCliService octoCliService) {
        checkNotNull(octoCliService, "octoCliService cannot be null");
        this.octoCliService = octoCliService;
    }

    /**
     * Get the list of OctopusDeployServer from OctopusDeployPlugin configuration
     * @return all configured servers
     * */
    public static List<OctopusDeployServer> getOctopusDeployServers() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            throw new IllegalStateException("Jenkins instance is null");
        }
        OctopusDeployPlugin.DescriptorImpl descriptor = (OctopusDeployPlugin.DescriptorImpl) jenkinsInstance.getDescriptor(OctopusDeployPlugin.class);
        return descriptor.getOctopusDeployServers();
    }


    public static List<String> getOctopusDeployServersIds() {

        List<String> ids = new ArrayList<>();
        for (OctopusDeployServer s:getOctopusDeployServers()) {
            ids.add(s.getId());
        }
        return ids;
    }

    public static OctoInstallation[] getOctopusToolInstallations() {
        OctoInstallation.DescriptorImpl descriptor = (OctoInstallation.DescriptorImpl) Jenkins.getInstance().getDescriptor(OctoInstallation.class);
        return descriptor.getInstallations();
    }

    public static List<String> getOctopusToolIds() {
        List<String> ids = new ArrayList<>();
        for (OctoInstallation i : getOctopusToolInstallations()) {
            ids.add(i.getName());
        }
        return ids;
    }

    /**
     * Get the instance of OctopusDeployServer by serverId
     * @param serverId The id of OctopusDeployServer in the configuration.
     * @return the server by id
     * */
    public static OctopusDeployServer getOctopusDeployServer(String serverId) {
        if (serverId == null || serverId.isEmpty()){
            return getDefaultOctopusDeployServer();
        }
        for(OctopusDeployServer server : getOctopusDeployServers()) {
            if(server.getId().equals(serverId)) {
                return server;
            }
        }
        return null;
    }

    public Boolean hasAdvancedOptions() {
        return getVerboseLogging() || (getAdditionalArgs() != null && !getAdditionalArgs().isEmpty());
    }

    /**
     * Get OctopusApi instance for this deployment
     * @return the api for a given server
     */
    public OctopusApi getApi() {
        return getOctopusDeployServer().getApi();
    }

    Boolean[] getMasks(List<String> commands, String... commandArgumentsToMask) {
        final Boolean[] masks = new Boolean[commands.size()];
        Arrays.fill(masks, Boolean.FALSE);
        for(String commandArgumentToMask : commandArgumentsToMask) {
            if(commands.contains(commandArgumentToMask)) {
                masks[commands.indexOf(commandArgumentToMask) + 1] = Boolean.TRUE;
            }
        }
        return masks;
    }

    @Nullable
    protected EnvVars getEnvVars(@Nonnull Run<?, ?> run, @Nonnull TaskListener listener, Log log) {
        EnvVars envVars;
        try {
            envVars = run.getEnvironment(listener);
        } catch (Exception ex) {
            log.fatal(String.format("Failed to retrieve environment variables for this build '%s' - '%s'",
                    run.getParent().getName(), ex.getMessage()));
            run.setResult(Result.FAILURE);
            return null;
        }
        return envVars;
    }

    protected boolean CheckForFailedTask(@Nonnull Run<?, ?> run, Log log) {
        if (Result.FAILURE.equals(run.getResult())) {
            log.info("Not packaging the application due to job being in FAILED state.");
            return true;
        }
        return false;
    }

    @NotNull
    protected Log getLog(@Nonnull TaskListener listener) {
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);
        return new Log(listenerAdapter);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public static abstract class AbstractOctopusDeployDescriptorImplStep extends BuildStepDescriptor<Builder>
    {
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            save();
            return true;
        }

        protected OctopusApi getApiByServerId(String serverId){
            return AbstractOctopusDeployRecorderPostBuildStep.getOctopusDeployServer(serverId).getApi();
        }

        public String getDefaultOctopusDeployServerId() {
            OctopusDeployServer server = AbstractOctopusDeployRecorderPostBuildStep.getDefaultOctopusDeployServer();
            if(server != null){
                return server.getId();
            }
            return null;
        }

        public String getDefaultOctopusToolId() {
            OctoInstallation tool = OctoInstallation.getDefaultInstallation();
            if (tool != null) {
                return tool.getName();
            }
            return null;
        }

        /**
         * Check that the serverId field is not empty and does exist.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckServerId(@QueryParameter String serverId) {
            serverId = serverId.trim();
            return OctopusValidator.validateServerId(serverId);
        }

        /**
         * Data binding that returns all configured Octopus server ids to be used in the serverId drop-down list.
         * @return ComboBoxModel
         */
        public ComboBoxModel doFillServerIdItems() {
            return new ComboBoxModel(getOctopusDeployServersIds());
        }

        public ComboBoxModel doFillToolIdItems() {
            return new ComboBoxModel(getOctopusToolIds());
        }

        public ListBoxModel doFillSpaceIdItems(@QueryParameter String serverId) {
            ListBoxModel spaceItems = new ListBoxModel();
            if(doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return spaceItems;
            }

            OctopusApi api = getApiByServerId(serverId).forSystem();
            try {
                Set<Space> spaces = api.getSpacesApi().getAllSpaces();
                spaceItems.add("", "");
                for (Space space : spaces) {
                    spaceItems.add(space.getName(), space.getId());
                }
            } catch (Exception ex) {
                Logger.getLogger(AbstractOctopusDeployRecorderPostBuildStep.class.getName()).log(Level.SEVERE, null, ex);
            }

            return spaceItems;
        }
    }
}
