package hudson.plugins.octopusdeploy;

import com.google.common.base.Splitter;
import com.octopusdeploy.api.data.*;
import com.octopusdeploy.api.*;
import java.io.*;
import java.util.*;

import com.octopusdeploy.api.data.Environment;
import com.octopusdeploy.api.data.Project;
import hudson.*;
import hudson.model.*;
import hudson.plugins.octopusdeploy.cli.OctopusCliExecutor;
import hudson.plugins.octopusdeploy.cli.OctopusCliWrapperBuilder;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.util.BuildListenerAdapter;
import net.sf.json.*;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.*;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkState;
import static hudson.plugins.octopusdeploy.services.StringUtil.sanitizeValue;

/**
 * Executes deployments of releases.
 */
public class OctopusDeployDeploymentRecorder extends AbstractOctopusDeployRecorderPostBuildStep implements Serializable {

    /**
     * The release version number in Octopus.
     */
    private final String releaseVersion;
    public String getReleaseVersion() {
        return releaseVersion;
    }

    @DataBoundConstructor
    public OctopusDeployDeploymentRecorder(String serverId, String toolId, String spaceId, String project,
                                           String releaseVersion, String environment) {
        this.serverId = sanitizeValue(serverId);
        this.toolId = sanitizeValue(toolId);
        this.spaceId = sanitizeValue(spaceId);
        this.project = sanitizeValue(project);
        this.releaseVersion = sanitizeValue(releaseVersion);
        this.setEnvironment(environment);
        this.cancelOnTimeout = false;
        this.waitForDeployment = false;
        this.verboseLogging = false;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws AbortException {
        // This method deserves a refactor and cleanup.
        boolean success = true;
        BuildListenerAdapter listenerAdapter = new BuildListenerAdapter(listener);

        Log log = new Log(listenerAdapter);
        if (Result.FAILURE.equals(run.getResult())) {
            log.info("Not deploying due to job being in FAILED state.");
            return;
        }

        EnvVars envVars;
        try {
            envVars = run.getEnvironment(listener);
        } catch (Exception ex) {
            log.fatal(String.format("Failed to retrieve environment variables for this build - '%s'", getExceptionMessage(ex)));
            run.setResult(Result.FAILURE);
            return;
        }
        VariableResolver resolver =  new VariableResolver.ByMap<>(envVars);
        EnvironmentVariableValueInjector envInjector = new EnvironmentVariableValueInjector(resolver, envVars);

        // NOTE: hiding the member variables of the same name with their env-injected equivalents
        String project = envInjector.injectEnvironmentVariableValues(this.project);
        String releaseVersion = envInjector.injectEnvironmentVariableValues(this.releaseVersion);
        String environment = envInjector.injectEnvironmentVariableValues(this.environment);
        String tenant = envInjector.injectEnvironmentVariableValues(this.tenant);
        String variables = envInjector.injectEnvironmentVariableValues(this.variables);

        logStartHeader(log);

        checkState(StringUtils.isNotBlank(project), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Project name"));
        checkState(StringUtils.isNotBlank(environment), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Environment name"));
        checkState(StringUtils.isNotBlank(releaseVersion), String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Version"));

        // Parse variables
        List<String> variableCommands = getVariableCommands(run, envInjector, log, variables);
        if (run.getResult() == Result.FAILURE) {
            return;
        }

        try {
            // Parse environment (can be comma-separated, but wrapper expects single value)
            // For now, take the first environment
            final Iterable<String> environmentNameSplit = Splitter.on(',')
                    .trimResults()
                    .omitEmptyStrings()
                    .split(environment);
            String firstEnvironment = environmentNameSplit.iterator().next();

            // Parse tenant (can be comma-separated, but wrapper expects single value)
            String firstTenant = null;
            if (StringUtils.isNotBlank(tenant)) {
                Iterable<String> tenantsSplit = Splitter.on(',')
                        .trimResults()
                        .omitEmptyStrings()
                        .split(tenant);
                firstTenant = tenantsSplit.iterator().next();
            }

            // Create wrapper
            OctopusCliExecutor wrapper = new OctopusCliWrapperBuilder(
                    getToolId(), workspace, launcher, envVars, listenerAdapter)
                    .serverId(this.serverId)
                    .spaceId(spaceId)
                    .projectName(project)
                    .verboseLogging(verboseLogging)
                    .build();

            // Execute deploy-release command
            Result result = wrapper.deployRelease(
                    releaseVersion,
                    firstEnvironment,
                    firstTenant,
                    tenantTag,
                    variableCommands,
                    waitForDeployment,
                    deploymentTimeout,
                    cancelOnTimeout,
                    additionalArgs);

            success = result.equals(Result.SUCCESS);
            if (success) {
                AddBuildSummary(run, log, project, releaseVersion, firstEnvironment, firstTenant);
            }
        } catch (Exception ex) {
            log.fatal("Failed to deploy: " + getExceptionMessage(ex));
            success = false;
        }

        if (!success) {
            throw new AbortException("Failed to deploy");
        }
    }

    private void AddBuildSummary(@NotNull Run<?, ?> run, Log log, String project, String releaseVersion, String environment, String tenant) {
        try {
            OctopusDeployServer octopusDeployServer = OctopusDeployPlugin.getOctopusDeployServer(serverId);
            String serverUrl = octopusDeployServer.getUrl();
            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            }

            OctopusApi api = octopusDeployServer.getApi().forSpace(spaceId);
            Project fullProject = api.getProjectsApi().getProjectByName(project, true);
            Environment fullEnvironment = api.getEnvironmentsApi().getEnvironmentByName(environment, true);

            String tenantId = null;
            if (tenant != null && !tenant.isEmpty()) {
                Tenant fullTenant = api.getTenantsApi().getTenantByName(tenant, true);
                tenantId = fullTenant.getId();
            }

            String urlSuffix = api.getDeploymentsApi().getPortalUrlForDeployment(fullProject.getId(), releaseVersion, fullEnvironment.getId(), tenantId);

            if (urlSuffix != null && !urlSuffix.isEmpty()) {
                String portalUrl = serverUrl + urlSuffix;
                log.info("Deployment executed: \n\t" + portalUrl);
                run.addAction(new BuildInfoSummary(BuildInfoSummary.OctopusDeployEventType.Deployment, portalUrl));
            }
        } catch (Exception ex) {
            log.error("Failed to generate build summary: " + getExceptionMessage(ex));
        }
    }

    /**
     * Write the startup header for the logs to show what our inputs are.
     * @param log The logger
     */
    private void logStartHeader(Log log) {
        log.info("Started Octopus Deploy");
        log.info("======================");
        log.info("Project: " + project);
        log.info("Version: " + releaseVersion);
        log.info("Environment: " + environment);
        if (tenant != null && !tenant.isEmpty()) {
            log.info("Tenant: " + tenant);
        }
        log.info("======================");
    }

    /**
     * Descriptor for {@link OctopusDeployDeploymentRecorder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    @Symbol("octopusDeployRelease")
    public static final class DescriptorImpl extends AbstractOctopusDeployDescriptorImplPost {
        private static final String PROJECT_RELEASE_VALIDATION_MESSAGE = "Project must be set to validate release.";
        private static final String SERVER_ID_VALIDATION_MESSAGE = "Could not validate without a valid Server ID.";

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Octopus Deploy: Deploy Release";
        }

        /**
         * Check that the project field is not empty and is a valid project.
         * @param project The name of the project.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckProject(@QueryParameter String project, @QueryParameter String serverId, @QueryParameter String spaceId) {
            project = project.trim();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return FormValidation.warning(SERVER_ID_VALIDATION_MESSAGE);
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            OctopusValidator validator = new OctopusValidator(api);
            return validator.validateProject(project);
        }

        /**
         * Check that the deployment timeout is valid.
         * @param deploymentTimeout The deployment timeout (TimeSpan).
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckDeploymentTimeout(@QueryParameter String deploymentTimeout) {
            return OctopusValidator.validateDeploymentTimeout(deploymentTimeout);
        }

        /**
         * Check that the releaseVersion field is not empty.
         * @param releaseVersion The release version of the package.
         * @param project The project name
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckReleaseVersion(@QueryParameter String releaseVersion, @QueryParameter String project, @QueryParameter String serverId, @QueryParameter String spaceId) {
            releaseVersion = releaseVersion.trim();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return FormValidation.warning(SERVER_ID_VALIDATION_MESSAGE);
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            if (project == null || project.isEmpty()) {
                return FormValidation.warning(PROJECT_RELEASE_VALIDATION_MESSAGE);
            }
            com.octopusdeploy.api.data.Project p;
            try {
                p = api.getProjectsApi().getProjectByName(project);
                if (p == null) {
                    return FormValidation.warning("Unable to validate release because the project '%s' couldn't be found.", project);
                }
            } catch (Exception ex) {
                return FormValidation.warning(PROJECT_RELEASE_VALIDATION_MESSAGE);
            }

            OctopusValidator validator = new OctopusValidator(api);
            return validator.validateRelease(releaseVersion, p, OctopusValidator.ReleaseExistenceRequirement.MustExist);
        }


        /**
         * Check that the environment field is not empty.
         * @param environment The name of the project.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return Ok if not empty, error otherwise.
         */
        public FormValidation doCheckEnvironment(@QueryParameter String environment, @QueryParameter String serverId, @QueryParameter String spaceId) {
            environment = environment.trim();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return FormValidation.warning(SERVER_ID_VALIDATION_MESSAGE);
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            OctopusValidator validator = new OctopusValidator(api);
            return validator.validateEnvironment(environment);
        }

        /**
         * Data binding that returns all possible environment names to be used in the environment autocomplete.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return ComboBoxModel
         */
        public ComboBoxModel doFillEnvironmentItems(@QueryParameter String serverId, @QueryParameter String spaceId) {
            ComboBoxModel names = new ComboBoxModel();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return names;
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            try {
                Set<com.octopusdeploy.api.data.Environment> environments = api.getEnvironmentsApi().getAllEnvironments();
                for (com.octopusdeploy.api.data.Environment env : environments) {
                    names.add(env.getName());
                }
            } catch (Exception ex) {
                Logger.getLogger(OctopusDeployDeploymentRecorder.class.getName()).log(Level.SEVERE, null, ex);
            }
            return names;
        }

        /**
         * Data binding that returns all possible project names to be used in the project autocomplete.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return ComboBoxModel
         */
        public ComboBoxModel doFillProjectItems(@QueryParameter String serverId, @QueryParameter String spaceId) {
            ComboBoxModel names = new ComboBoxModel();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return names;
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            try {
                Set<com.octopusdeploy.api.data.Project> projects = api.getProjectsApi().getAllProjects();
                for (com.octopusdeploy.api.data.Project proj : projects) {
                    names.add(proj.getName());
                }
            } catch (Exception ex) {
                Logger.getLogger(OctopusDeployDeploymentRecorder.class.getName()).log(Level.SEVERE, null, ex);
            }
            return names;
        }

        /**
         * Data binding that returns all possible tenant names to be used in the tenant autocomplete.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return ComboBoxModel
         */
        public ComboBoxModel doFillTenantItems(@QueryParameter String serverId, @QueryParameter String spaceId) {
            ComboBoxModel names = new ComboBoxModel();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return names;
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            try {
                Set<com.octopusdeploy.api.data.Tenant> tenants = api.getTenantsApi().getAllTenants();
                for (com.octopusdeploy.api.data.Tenant ten : tenants) {
                    names.add(ten.getName());
                }
            } catch (Exception ex) {
                Logger.getLogger(OctopusDeployDeploymentRecorder.class.getName()).log(Level.SEVERE, null, ex);
            }
            return names;
        }

        /**
         * Data binding that returns all possible tenant tags to be used in the tenant tag autocomplete.
         * @param serverId The id of OctopusDeployServer in the configuration.
         * @param spaceId The id of the space where to load this resource from
         * @return ComboBoxModel
         */
        public ComboBoxModel doFillTenantTagItems(@QueryParameter String serverId, @QueryParameter String spaceId) {
            ComboBoxModel names = new ComboBoxModel();

            if (doCheckServerId(serverId).kind != FormValidation.Kind.OK) {
                return names;
            }

            OctopusApi api = getApiByServerId(serverId).forSpace(spaceId);
            try {
                Set<TagSet> tagSets = api.getTagSetsApi().getAll();
                for (TagSet tagSet : tagSets) {
                    for (Tag tag : tagSet.getTags()) {
                        names.add(tag.getCanonicalName());
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(OctopusDeployReleaseRecorder.class.getName()).log(Level.SEVERE, null, ex);
            }

            return names;
        }

    }
}
