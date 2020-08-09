package hudson.plugins.octopusdeploy;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.octopusdeploy.commands.PackCommand;
import hudson.plugins.octopusdeploy.commands.PackCommandParameters;
import hudson.plugins.octopusdeploy.services.OctoCliService;
import org.jetbrains.annotations.NotNull;
import org.junit.*;

import java.io.File;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertThat;

public class PackRecorderTest {

    PackCommand packCommand;
    FakeOctoCliLauncher octoCliService;

    public PackRecorderTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        this.octoCliService = new FakeOctoCliLauncher();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void basicPackCommand() {
        this.packCommand = new PackCommand(
                new PackCommandParameters(
                        this.octoCliService,
                        "toolId",
                        "packageId",
                        "zip",
                        ".",
                        "1.2.3",
                        ".",
                        "./artifacts",
                        null,
                        false,
                        false
                        ));

        packCommand.perform(new FilePath(new File("/tmp")), new Launcher.DummyLauncher(TaskListener.NULL), TaskListener.NULL, new EnvVars());

        List<String> commands = this.octoCliService.getCommands();
        assertThat(commands, hasItem("--format"));
        assertThat(commands, hasItem("zip"));
    }

    public class FakeOctoCliLauncher implements OctoCliService {

        private List<String> commands;
        public List<String> getCommands() {
            return this.commands;
        }

        private Result result;
        public void setResult(Result result) {
            this.result = result;
        }

        @Override
        public @NotNull Result launchOcto(FilePath workspace, Launcher launcher, List<String> commands, Boolean[] masks, EnvVars environment, BuildListener listener, String octoToolId) {
            this.commands = commands;
            return this.result == null ? Result.SUCCESS : this.result;
        }
    }
}
