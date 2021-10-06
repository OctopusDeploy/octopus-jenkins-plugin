package hudson.plugins.octopusdeploy;

import com.octopus.helper.BaseRecorderTest;
import com.octopus.sdk.domain.Project;
import com.octopus.sdk.domain.ProjectGroup;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class OctopusDeployReleaseRecorderDescriptorImplTest extends BaseRecorderTest {

    private final OctopusDeployReleaseRecorder.DescriptorImpl descriptor =
            new OctopusDeployReleaseRecorder.DescriptorImpl();

    @Test
    public void testDoCheckProject() {
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        spaceScopedClient.createProject("Proj1", projGroup.getProperties().getId());

        FormValidation validation = descriptor.doCheckProject("Proj1",
                JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckChannel() {
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        final Project project = spaceScopedClient.createProject("Proj1", projGroup.getProperties().getId());
        spaceScopedClient.createChannel("Channel1", project.getProperties().getId());

        FormValidation validation = descriptor.doCheckChannel("Channel1",
                "Proj1",
                JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testDoCheckReleaseVersionWithNullProjectFailsValidation() {
        FormValidation validation = descriptor.doCheckReleaseVersion("1.0.0",
                null,
                JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage()).isEqualTo("Project must be set to validate release.");
    }

    @Test
    public void testDoCheckReleaseVersionWithEmptyProjectFailsValidation() {
        FormValidation validation =
                descriptor.doCheckReleaseVersion("1.0.0",
                        "",
                        JENKINS_OCTOPUS_SERVER_ID,
                        spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage()).isEqualTo("Project must be set to validate release.");
    }

    @Test
    public void testDoCheckReleaseVersionWithoutCorrespondingProjectFailsValidation() {
        FormValidation validation =
                descriptor.doCheckReleaseVersion("1.0.0",
                        "Proj1",
                        JENKINS_OCTOPUS_SERVER_ID,
                        spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Unable to validate release because the project &#039;Proj1&#039; couldn&#039;t be found.");
    }

    @Test
    public void testDoCheckReleaseVersion() {
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        spaceScopedClient.createProject("Proj1", projGroup.getProperties().getId());

        FormValidation validation =
                descriptor.doCheckReleaseVersion("1.0.0",
                        "Proj1",
                        JENKINS_OCTOPUS_SERVER_ID,
                        spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void doCheckReleaseNotesFileWithReleaseNotesPassesValidation() {
        FormValidation validation = descriptor.doCheckReleaseNotesFile("Release Notes");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void doCheckReleaseNotesFileWithEmptyReleaseNotesFailsValidation() {
        FormValidation validation = descriptor.doCheckReleaseNotesFile("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please provide a project notes file.");
    }

    @Test
    public void testDoCheckEnvironment() {
        spaceScopedClient.createEnvironment("Env1");

        final FormValidation validation = descriptor.doCheckEnvironment("Env1",
                JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testDoFillEnvironmentItems() {
        List<String> environmentsToCreate = Arrays.asList("Env1", "Env2", "Env3");
        environmentsToCreate.forEach(name -> spaceScopedClient.createEnvironment(name));

        final ComboBoxModel model = descriptor.doFillEnvironmentItems(JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(model.size()).isEqualTo(environmentsToCreate.size());
        assertThat(model).containsAll(environmentsToCreate);
    }

    // TODO (scl): Create tenant tags and enable test
    @Test
    @Disabled
    public void testDoFillTenantItems() {
        List<String> tenantsToCreate = Arrays.asList("Tenant1", "Tenant2", "Tenant3");
        // Create Tenants

        final ListBoxModel model = descriptor.doFillTenantItems(JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(model.size()).isEqualTo(tenantsToCreate.size());
        assertThat(model).flatExtracting("name").containsAll(tenantsToCreate);
        assertThat(model).flatExtracting("value").containsAll(tenantsToCreate);
    }

    // TODO (scl): Create tenant tags and enable test
    @Test
    @Disabled
    public void testDoFillTenantTagItems() {
        List<String> tenantTagsToCreate = Arrays.asList("Tenant1", "Tenant2", "Tenant3");
        // Create Tenant Tags

        final ComboBoxModel model = descriptor.doFillTenantTagItems(JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(model.size()).isEqualTo(tenantTagsToCreate.size());
        assertThat(model).containsAll(tenantTagsToCreate);
    }

    @Test
    public void testDoFillProjectItems() {
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        List<String> projectsToCreate = Arrays.asList("Proj1", "Proj2", "Proj3");
        projectsToCreate.forEach(name -> spaceScopedClient.createProject(name, projGroup.getProperties().getId()));

        final ComboBoxModel model = descriptor.doFillProjectItems(JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(model.size()).isEqualTo(projectsToCreate.size());
        assertThat(model).containsAll(projectsToCreate);
    }

    @Test
    public void testDoFillChannelItems() {
        final String projectName = "Proj1";
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        final Project project = spaceScopedClient.createProject(projectName, projGroup.getProperties().getId());
        List<String> channelsToCreate = Arrays.asList("Channel1", "Channel2", "Channel3");
        channelsToCreate.forEach(name -> spaceScopedClient.createChannel(name, project.getProperties().getId()));

        final ComboBoxModel model = descriptor.doFillChannelItems(projectName,
                JENKINS_OCTOPUS_SERVER_ID,
                spaceScopedClient.getSpaceId());

        assertThat(model.size()).isEqualTo(channelsToCreate.size() + 1); // Increase by 1 for default channel
        assertThat(model).containsAll(channelsToCreate);
    }

}