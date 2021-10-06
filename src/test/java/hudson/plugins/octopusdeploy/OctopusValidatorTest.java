package hudson.plugins.octopusdeploy;

import com.octopus.helper.SpaceScopedClient;
import com.octopus.helper.TestHelper;
import com.octopus.sdk.domain.ProjectGroup;
import com.octopus.sdk.model.space.SpaceOverviewResource;
import com.octopus.testsupport.BaseOctopusServerEnabledTest;
import com.octopusdeploy.api.OctopusApi;
import com.octopusdeploy.api.data.Project;
import hudson.util.FormValidation;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OctopusValidatorTest extends BaseOctopusServerEnabledTest {

    private MockedStatic<AbstractOctopusDeployRecorderPostBuildStep> mockedPostBuildStep;
    private SpaceScopedClient spaceScopedClient = null;
    private OctopusValidator validator = null;


    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        spaceScopedClient = TestHelper.buildSpaceScopedClientForTesting(httpClient,
                server,
                TestHelper.generateSpaceName(testInfo.getDisplayName()));
        validator = new OctopusValidator(new OctopusApi(server.getOctopusUrl(), server.getApiKey())
                .forSpace(spaceScopedClient.getSpaceId()));
        mockedPostBuildStep = Mockito.mockStatic(AbstractOctopusDeployRecorderPostBuildStep.class);
    }

    @AfterEach
    public void cleanUp() throws IOException {
        mockedPostBuildStep.close();

        if (spaceScopedClient.getRepository() != null && spaceScopedClient.getSpace() != null) {
            final SpaceOverviewResource resource = spaceScopedClient.getSpace().getProperties();
            resource.setTaskQueueStopped(true);
            spaceScopedClient.getRepository().spaces().update(resource);
            spaceScopedClient.getRepository().spaces().delete(resource);
        }
    }

    @Test
    public void testValidateProjectEmptyProjectFailsValidation() {
        final FormValidation validation = validator.validateProject("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please provide a project name.");
    }

    @Test
    public void testValidateProjectWithoutCorrespondingProjectFailsValidation() {
        final FormValidation validation = validator.validateProject("Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Project &#039;Proj1&#039; doesn&#039;t exist. " +
                        "If this field is computed you can disregard this warning.");
    }

    @Test
    public void testValidateProjectWithoutCorrespondingProjectNameCaseFailsValidation() {
        spaceScopedClient.createProject("Proj1", "ProjGroup1");

        final FormValidation validation = validator.validateProject("proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Project name case does not match. Did you mean &#039;Proj1&#039;?");
    }

    @Test
    public void testValidateProjectWithCorrespondingProjectNamePassesValidation() {
        spaceScopedClient.createProject("Proj1", "ProjGroup1");
        final FormValidation validation = validator.validateProject("Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateChannelEmptyChannelPassesValidation() {
        final FormValidation validation = validator.validateChannel("", "Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateChannelEmptyProjectFailsValidation() {
        final FormValidation validation = validator.validateChannel("Channel1", "");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage()).isEqualTo("Project must be set to validate this field.");
    }

    @Test
    public void testValidateChannelWithoutCorrespondingProjectFailsValidation() {
        final FormValidation validation = validator.validateChannel("Channel1", "Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Unable to validate channel because the project &#039;Proj1&#039; couldn&#039;t be found.");
    }

    @Test
    public void testValidateChannelWithoutCorrespondingChannelFailsValidation() {
        spaceScopedClient.createProject("Proj1", "ProjGroup1");

        final FormValidation validation = validator.validateChannel("Channel1", "Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Channel &#039;Channel1&#039; doesn&#039;t exist. " +
                        "If this field is computed you can disregard this warning.");
    }

    @Test
    public void testValidateChannelWithCorrespondingChannelAndProjectPassesValidation() {
        final com.octopus.sdk.domain.Project project =
                spaceScopedClient.createProject("Proj1", "ProjGroup1");
        spaceScopedClient.createChannel("Channel1", project.getProperties().getId());

        final FormValidation validation = validator.validateChannel("Channel1", "Proj1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateEnvironmentWithEmptyEnvironmentNameFailsValidation() {
        final FormValidation validation = validator.validateEnvironment("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please provide an environment name.");
    }

    @Test
    public void testValidateEnvironmentWithoutEnvironmentsFailsValidation() {
        final FormValidation validation = validator.validateEnvironment("Env1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Environment &#039;Env1&#039; doesn&#039;t exist. " +
                        "If this field is computed you can disregard this warning.");
    }

    @Test
    public void testValidateEnvironmentWithUnmatchedEnvironmentFailsValidation() {
        spaceScopedClient.createEnvironment("Env1");

        final FormValidation validation = validator.validateEnvironment("env1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Environment name case does not match. Did you mean &#039;Env1&#039;?");
    }

    @Test
    public void testDoCheckEnvironmentWithCorrespondingEnvironmentPassesValidation() {
        spaceScopedClient.createEnvironment("Env1");

        final FormValidation validation = validator.validateEnvironment("Env1");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateReleaseWithEmptyReleaseVersionFailsValidation() {
        final FormValidation validation = validator.validateRelease("",
                new Project("id", "name"),
                OctopusValidator.ReleaseExistenceRequirement.MustNotExist);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please provide a release version.");
    }

    @Test
    public void testValidateReleaseWhereReleaseMustExistFailsValidation() {
        final ProjectGroup projGroup = spaceScopedClient.createProjectGroup("ProjGroup1");
        final com.octopus.sdk.domain.Project newProject =
                spaceScopedClient.createProject("Proj1", projGroup.getProperties().getId());
        final Project project = new Project(newProject.getProperties().getId(), newProject.getProperties().getName());

        final FormValidation validation = validator.validateRelease("1.0.0",
                project,
                OctopusValidator.ReleaseExistenceRequirement.MustExist);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Release 1.0.0 doesn&#039;t exist for project &#039;Proj1&#039;. " +
                        "If this field is computed you can disregard this warning.");
    }

    // TODO (scl): Create release and enable test
    @Test
    @Disabled
    public void testValidateReleaseWhereReleaseMustExistPassesValidation() {
        final com.octopus.sdk.domain.Project newProject =
                spaceScopedClient.createProject("Proj1", "ProjGroup1");
        // Create release for Proj1 with matching release version
        final Project project = new Project(newProject.getProperties().getId(), newProject.getProperties().getName());

        final FormValidation validation = validator.validateRelease("1.0.0",
                project,
                OctopusValidator.ReleaseExistenceRequirement.MustExist);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    // TODO (scl): - Create release and enable test
    @Test
    @Disabled
    public void testValidateReleaseWhereReleaseMustNotExistFailsValidation() {
        final com.octopus.sdk.domain.Project newProject =
                spaceScopedClient.createProject("Proj1", "ProjGroup1");
        // Create release for Proj1 with matching release version
        final Project project = new Project(newProject.getProperties().getId(), newProject.getProperties().getName());

        final FormValidation validation = validator.validateRelease("1.0.0",
                project,
                OctopusValidator.ReleaseExistenceRequirement.MustNotExist);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.WARNING);
        assertThat(validation.getMessage())
                .isEqualTo("Release 1.0.0 doesn&#039;t exist for project &#039;Proj1&#039;. " +
                        "If this field is computed you can disregard this warning.");
    }

    @Test
    public void testValidateReleaseWhereReleaseMustNotExistPassesValidation() {
        final com.octopus.sdk.domain.Project newProject =
                spaceScopedClient.createProject("Proj1", "ProjGroup1");
        final Project project = new Project(newProject.getProperties().getId(), newProject.getProperties().getName());

        final FormValidation validation = validator.validateRelease("1.0.0",
                project,
                OctopusValidator.ReleaseExistenceRequirement.MustNotExist);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateServerIdWithNullServerIdFailsValidation() {
        final FormValidation validation = OctopusValidator.validateServerId(null);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please select an instance of Octopus Deploy.");
    }

    @Test
    public void testValidateServerIdWithEmptyServerIdFailsValidation() {
        final FormValidation validation = OctopusValidator.validateServerId("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("Please select an instance of Octopus Deploy.");
    }

    @Test
    public void testValidateServerIdWithDefaultServerIdPassesValidation() {
        final FormValidation validation = OctopusValidator.validateServerId("default");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateServerIdWithNoConfiguredServersFailsValidation() {
        mockedPostBuildStep
                .when(AbstractOctopusDeployRecorderPostBuildStep::getOctopusDeployServersIds)
                .thenReturn(Collections.EMPTY_LIST);

        final FormValidation validation = OctopusValidator.validateServerId("someId");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("There are no Octopus Deploy servers configured.");
    }

    @Test
    public void testValidateServerIdWithNoMatchingServersFailsValidation() {
        mockedPostBuildStep
                .when(AbstractOctopusDeployRecorderPostBuildStep::getOctopusDeployServersIds)
                .thenReturn(Collections.singletonList("someId"));

        final FormValidation validation = OctopusValidator.validateServerId("otherId");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage())
                .isEqualTo("There are no Octopus Deploy servers configured with this Server Id.");
    }

    @Test
    public void testValidateServerIdWithMatchingServerPassesValidation() {
        mockedPostBuildStep
                .when(AbstractOctopusDeployRecorderPostBuildStep::getOctopusDeployServersIds)
                .thenReturn(Collections.singletonList("someId"));

        final FormValidation validation = OctopusValidator.validateServerId("someId");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateDirectoryWithNullPathPassesValidation() {
        final FormValidation validation = OctopusValidator.validateDirectory(null);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateDirectoryWithEmptyPathPassesValidation() {
        final FormValidation validation = OctopusValidator.validateDirectory("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateDirectoryWithMissingPathFailsValidation() {
        final FormValidation validation = OctopusValidator.validateDirectory("bad-path");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage()).isEqualTo("This is not a path to a directory");
    }

    @Test
    public void testValidateDeploymentTimeoutWithNullTimeoutPassesValidation() {
        final FormValidation validation = OctopusValidator.validateDeploymentTimeout(null);

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateDeploymentTimeoutWithEmptyTimeoutPassesValidation() {
        final FormValidation validation = OctopusValidator.validateDeploymentTimeout("");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }

    @Test
    public void testValidateDeploymentTimeoutWithInvalidTimeoutFormatFailsValidation() {
        final FormValidation validation = OctopusValidator.validateDeploymentTimeout("invalid");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.ERROR);
        assertThat(validation.getMessage())
                .isEqualTo("This is not a valid deployment timeout it should be in the format HH:mm:ss");
    }

    @Test
    public void testValidateDeploymentTimeoutWithValidTimeoutFormatPassesValidation() {
        final FormValidation validation = OctopusValidator.validateDeploymentTimeout("11:30:00");

        assertThat(validation.kind).isEqualTo(FormValidation.Kind.OK);
    }
}