package org.jenkinsci.plugins.drupal;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.plugins.git.GitTool;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;

import net.sf.json.JSONObject;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link DrupalInstanceBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Fengtan
 */
public class DrupalInstanceBuilder extends Builder {

    private final String db;
    private final boolean coder;
    private final boolean simpletest;
    private final String uri;
    
    @DataBoundConstructor
    public DrupalInstanceBuilder(String db, boolean coder, boolean simpletest, String uri) {
        this.db = db;
        this.coder = coder;
        this.simpletest = simpletest;
        this.uri = uri;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	// Create Drupal directory.
    	// TODO allow user to use a different subdirectory
    	File root = new File(build.getWorkspace().getRemote(), "drupal");
    	root.mkdir(); // TODO what if already exists

    	// Clone Drupal code.
    	EnvVars env = build.getEnvironment(listener);
    	String exe = GitTool.getDefaultInstallation().getGitExe();
    	GitClient git = Git.with(listener, env).in(root).using(exe).getClient();
    	if (git.hasGitRepo()) {
    		// Drupal code already cloned, do nothing.
    		// TODO unlesss user wants to re-clone Drupal
    	    // TODO if user changed version, re-checkout (even if no rebuild)
    		// TODO could be a git repo checked out by user (i.e. not drupal)
    		listener.getLogger().println("Drupal code detected, no need to clone");
    	} else {
        	listener.getLogger().println("Cloning Drupal, please be patient"); // TODO mention version/tag
        	git.clone("http://git.drupal.org/project/drupal.git", "origin", false, null);
        	git.checkoutBranch("7.x", "tags/7.37"); // TODO let user select version/tag
        	// TODO throw exception if clone fails
        	// TODO put URL in properties file / constant
    	}
    	
    	// Build Drupal instance.
    	DrushInvocation drush = new DrushInvocation(root, build, launcher, listener);
    	drush.siteInstall(db); // TODO do not re-install if user said so
    	
    	// Run Coder Review.
    	if (coder) {
    		// TODO coder version should be selectable from UI
    		// TODO do not download module is already exists -- makes the task slow
    		drush.download("coder-7.x-2.5");
    		drush.enable("coder_review");
    		drush.coderReview();
    	}
    	
    	// Run Simpletest.
    	if (simpletest) {
    		drush.enable("simpletest");
    		drush.testRun(uri);
    	}
    	
    	return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link DrupalInstanceBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/drupal/DrupalInstanceBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * In order to load the persisted global configuration, you have to 
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user. 
         */
        public FormValidation doCheckDb(@QueryParameter String value) {
            if (value.length() == 0) {
              return FormValidation.error("Please set a database URL"); // TODO check DB connection works
            }
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Build a Drupal instance";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req,formData);
        }
    }
}

