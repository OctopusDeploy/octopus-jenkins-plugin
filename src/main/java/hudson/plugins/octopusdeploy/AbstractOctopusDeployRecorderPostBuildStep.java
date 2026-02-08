package hudson.plugins.octopusdeploy;

import com.octopusdeploy.api.OctopusApi;
import com.octopusdeploy.api.data.Space;
import hudson.EnvVars;
import hudson.model.*;
import hudson.plugins.octopusdeploy.utils.JenkinsHelpers;
import hudson.tasks.*;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

/**
 * The AbstractOctopusDeployRecorder tries to take care of most of the Octopus
 * Deploy server access.
 * 
 * @author wbenayed
 */
public abstract class AbstractOctopusDeployRecorderPostBuildStep extends Recorder implements SimpleBuildStep {

    /**
     * Cache for OctopusDeployServer instance used in deployment
     * transient keyword prevents leaking API key to Job configuration
     */
    protected transient OctopusDeployServer octopusDeployServer;

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
            return OctopusDeployPlugin.getDefaultOctopusDeployServer().getApi().forSystem().getSupportsSpaces();
        } catch (Exception ex) {
            Logger.getLogger(AbstractOctopusDeployRecorderPostBuildStep.class.getName()).log(Level.SEVERE, null, ex);
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

    @DataBoundSetter
    public void setEnvironment(String environment) {
        this.environment = sanitizeValue(environment);
    }

    /**
     * The variables to use for a deploy in Octopus.
     */
    protected String variables;
    public String getVariables() {
        return variables;
    }

    @DataBoundSetter
    public void setVariables(String variables) {
        this.variables = variables;
    }

    /**
     * The Tenant to use for a deploy to in Octopus.
     */
    protected String tenant;
    public String getTenant() {
        return tenant;
    }

    @DataBoundSetter
    public void setTenant(String tenant) {
        this.tenant = sanitizeValue(tenant);
    }

    protected String tenantTag;
    public String getTenantTag() {
        return tenantTag;
    }

    @DataBoundSetter
    public void setTenantTag(String tenantTag) { this.tenantTag = sanitizeValue(tenantTag); }

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

    @DataBoundSetter
    public void setWaitForDeployment(boolean waitForDeployment) {
        this.waitForDeployment = waitForDeployment;
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

    @DataBoundSetter
    public void setDeploymentTimeout(String deploymentTimeout) { this.deploymentTimeout = sanitizeValue(deploymentTimeout); }

    /**
     * Whether to cancel the deployment if the deployment timeout is reached
     */
    protected boolean cancelOnTimeout;
    public boolean getCancelOnTimeout() {
        return cancelOnTimeout;
    }

    @DataBoundSetter
    public void setCancelOnTimeout(boolean cancelOnTimeout) {
        this.cancelOnTimeout = cancelOnTimeout;
    }

    public static OctoInstallation[] getOctopusToolInstallations() {
        Jenkins jenkins = JenkinsHelpers.getJenkins();
        OctoInstallation.DescriptorImpl descriptor = (OctoInstallation.DescriptorImpl) jenkins.getDescriptor(OctoInstallation.class);
        return descriptor.getInstallations();
    }

    public Boolean hasAdvancedOptions() {
        return getVerboseLogging() || (getAdditionalArgs() != null && !getAdditionalArgs().isEmpty());
    }

    protected List<String> getVariableCommands(@Nonnull Run<?, ?> run, EnvironmentVariableValueInjector envInjector, Log log, String variables) {
        Properties properties = new Properties();
        if (variables != null && !variables.isEmpty()) {
            try {
                properties.load(new StringReader(variables));
            } catch (Exception ex) {
                log.fatal(String.format("Unable to load entry variables: '%s'", getExceptionMessage(ex)));
                run.setResult(Result.FAILURE);
            }
        }

        List<String> variableCommands = new ArrayList<>();

        for(String variableName : properties.stringPropertyNames()) {
            String variableValue = envInjector.injectEnvironmentVariableValues(properties.getProperty(variableName));
            variableCommands.add(String.format("%s:%s", variableName, variableValue));
        }

        return variableCommands;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    protected static String getExceptionMessage(Exception ex) {
        String exceptionMessage = ex.getMessage();
        if (exceptionMessage == null) {
            exceptionMessage = ex.toString();
        }

        String stackTrace = ExceptionUtils.getFullStackTrace(ex);
        return exceptionMessage + "\n" + stackTrace;
    }

    public static abstract class AbstractOctopusDeployDescriptorImplPost extends BuildStepDescriptor<Publisher>
    {

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            save();
            return true;
        }

        protected OctopusApi getApiByServerId(String serverId) {
            return OctopusDeployPlugin.getOctopusDeployServer(serverId).getApi();
        }

        public String getDefaultOctopusDeployServerId() {
            OctopusDeployServer server = OctopusDeployPlugin.getDefaultOctopusDeployServer();
            if (server != null) {
                return server.getServerId();
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
            return new ComboBoxModel(OctopusDeployPlugin.getOctopusDeployServersIds());
        }

        public ComboBoxModel doFillToolIdItems() {
            return new ComboBoxModel(OctopusDeployPlugin.getOctopusToolIds());
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
