package org.jenkinsci.plugins.drupal.builders;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import net.sf.json.JSONObject;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.io.FilenameUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.plugins.drupal.beans.DrushInvocation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link CoderReviewBuilder} is created. The created
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
public class CoderReviewBuilder extends Builder {

	public final boolean style;
	public final boolean comment;
	public final boolean sql;
	public final boolean security;
	public final boolean i18n;
	
	public final String root;
	public final String logs;
	public final String except;
	
    @DataBoundConstructor
    public CoderReviewBuilder(boolean style, boolean comment, boolean sql, boolean security, boolean i18n, String root, String logs, String except) {
    	this.style = style;
    	this.comment = comment;
    	this.sql = sql;
    	this.security = security;
    	this.i18n = i18n;
    	this.root = root;
    	this.logs = logs;
    	this.except = except;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    	// Make sure logs directory exists.
    	File logsDir = new File(build.getWorkspace().getRemote(), logs);
    	logsDir.mkdir(); // TODO what if already exists

    	// Run Coder Review.
    	final File rootDir = new File(build.getWorkspace().getRemote(), root);
    	DrushInvocation drush = new DrushInvocation(new FilePath(rootDir), build.getWorkspace(), launcher, listener);
    	// TODO do not download module if already exists -- makes the task slow
		drush.download("coder-7.x-2.5", "modules"); // TODO coder version should be selectable from UI
		drush.enable("coder_review"); // TODO unless already enabled

		Collection<String> reviews = new HashSet<String>();
		if (this.style)    reviews.add("style");
		if (this.comment)  reviews.add("comment");
		if (this.sql)      reviews.add("sql");
		if (this.security) reviews.add("security");
		if (this.i18n)     reviews.add("i18n");

		// Remove projects the user wants to exclude.
		// **/*.info matches all modules, themes and installation profiles.
		// Installation profiles cannot be reviewed and will be just ignored by Coder.
		// TODO array_diff with drush pml --status=enabled --format=json ? 
		FileSet fileSet = Util.createFileSet(rootDir, "**/*.info", except);
		DirectoryScanner scanner = fileSet.getDirectoryScanner();
		Collection<String> projects = Arrays.asList(scanner.getIncludedFiles());
		
		// Transform sites/all/modules/mymodule/mymodule.module into mymodule.
		CollectionUtils.transform(projects, new Transformer<String, String>() {
			@Override
			public String transform(String project) {
				return FilenameUtils.getBaseName(project);
			}
		});

		// Run the code review.
		drush.coderReview(logsDir, reviews, projects);

    	return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * Load the persisted global configuration.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * This builder can be used with all kinds of project types.
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) { 
            return true;
        }
        
		// TODO-0 validate that rootDir exists (for all builders)

        /**
         * Human readable name used in the configuration screen.
         */
        public String getDisplayName() {
            return "Run Coder Review on Drupal";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }
}

