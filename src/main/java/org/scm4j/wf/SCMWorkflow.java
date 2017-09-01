package org.scm4j.wf;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.scm4j.vcs.api.IVCS;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.wf.actions.ActionKind;
import org.scm4j.wf.actions.ActionNone;
import org.scm4j.wf.actions.IAction;
import org.scm4j.wf.branch.DevelopBranch;
import org.scm4j.wf.branch.DevelopBranchStatus;
import org.scm4j.wf.branch.ReleaseBranch;
import org.scm4j.wf.branch.ReleaseBranchStatus;
import org.scm4j.wf.conf.Component;
import org.scm4j.wf.conf.Option;
import org.scm4j.wf.conf.VCSRepositories;
import org.scm4j.wf.conf.Version;
import org.scm4j.wf.exceptions.EComponentConfig;
import org.scm4j.wf.scmactions.CommitsFile;
import org.scm4j.wf.scmactions.ReleaseReason;
import org.scm4j.wf.scmactions.SCMActionBuild;
import org.scm4j.wf.scmactions.SCMActionForkReleaseBranch;
import org.scm4j.wf.scmactions.SCMActionTagRelease;

public class SCMWorkflow {

	public static final String MDEPS_FILE_NAME = "mdeps";
	public static final String VER_FILE_NAME = "version";
	public static final String MDEPS_CHANGED_FILE_NAME = "mdeps-changed"; //FIXME: how to use?
	public static final String COMMITS_FILE_NAME = "commits.yml"; 
	public static final File BASE_WORKING_DIR = new File(System.getProperty("user.home"), ".scm4j");

	private final VCSRepositories repos;
	private final List<Option> options;
	
	public SCMWorkflow(VCSRepositories repos, List<Option> options) {
		this.repos = repos;
		this.options = options;
	}
	
	public SCMWorkflow() {
		this(new ArrayList<Option>());
	}
	
	public SCMWorkflow(List<Option> options) {
		this(VCSRepositories.loadVCSRepositories(), options);
	}
	
	public static List<Option> parseOptions(String[] args) {
		List<Option> options = new ArrayList<>();
		for (String arg : args) {
			if (Option.getArgsMap().containsKey(arg)) {
				options.add(Option.getArgsMap().get(arg));
			}
		}
		return options;
	}
	
	public static File getCommitsFile() {
		return new File(SCMWorkflow.COMMITS_FILE_NAME);
	}
	
	public IAction getProductionReleaseAction(String componentName) {
		return getProductionReleaseAction(new Component(componentName, repos), ActionKind.AUTO);
	}
	
	public IAction getProductionReleaseAction(String componentName, ActionKind actionKind) {
		return getProductionReleaseAction(new Component(componentName, repos), actionKind);
	}
	
	public IAction getProductionReleaseAction(Component comp) {
		return getProductionReleaseAction(comp, ActionKind.AUTO);
	}

	public IAction getProductionReleaseAction(Component comp, ActionKind actionKind) {
		List<IAction> childActions = new ArrayList<>();
		DevelopBranch db = new DevelopBranch(comp);
		List<Component> devMDeps = db.getMDeps();

		for (Component mDep : devMDeps) {
			childActions.add(getProductionReleaseAction(mDep, actionKind));
		}

		return getProductionReleaseActionRoot(comp, childActions, actionKind);
	}
		
	public IAction getProductionReleaseActionRoot(Component comp, List<IAction> childActions, ActionKind actionKind) {
		DevelopBranch db = new DevelopBranch(comp);
		if (!db.hasVersionFile()) {
			throw new EComponentConfig("no " + VER_FILE_NAME + " file for " + comp.toString());
		}
		DevelopBranchStatus dbs = db.getStatus();

		if (dbs == DevelopBranchStatus.IGNORED) {
			return new ActionNone(comp, childActions, "develop branch is IGNORED");
		}

		ReleaseBranch rb = new ReleaseBranch(comp, repos);
		ReleaseBranchStatus rbs = rb.getStatus();

		if (rbs == ReleaseBranchStatus.MISSING) {
			skipAllBuilds(childActions);
			if (actionKind == ActionKind.BUILD) {
				return new ActionNone(comp, childActions, "nothing to build");
			}
			if (dbs == DevelopBranchStatus.MODIFIED) {
				return new SCMActionForkReleaseBranch(comp, childActions, ReleaseReason.NEW_FEATURES, options);
			} else {
				return new SCMActionForkReleaseBranch(comp, childActions, ReleaseReason.NEW_DEPENDENCIES, options);
			}
		}

		if (rbs == ReleaseBranchStatus.BRANCHED) {
			// BRANCHED - need to freeze mdeps
			// MDEPS_FROZEN - need to actualize mdeps
			skipAllBuilds(childActions);
			if (actionKind == ActionKind.BUILD) {
				return new ActionNone(comp, childActions, "nothing to build");
			}
			return new SCMActionForkReleaseBranch(comp, childActions, ReleaseReason.NEW_FEATURES, options);
		}

		if (rbs == ReleaseBranchStatus.MDEPS_FROZEN) {
			if (needToActualizeMDeps(childActions, rb)) {
				// need to actualize
				skipAllBuilds(childActions);
				if (actionKind == ActionKind.BUILD) {
					return new ActionNone(comp, childActions, "nothing to build");
				}
				return new SCMActionForkReleaseBranch(comp, childActions, ReleaseReason.ACTUALIZE_MDEPS, options);
			} else {
				// need to build
				skipAllForks(childActions);
				if (actionKind == ActionKind.FORK) {
					return new ActionNone(comp, childActions, "nothing to fork");
				}
				return new SCMActionBuild(comp, childActions, ReleaseReason.NEW_DEPENDENCIES, rb.getVersion(), options);
			}
		}

		if (rbs == ReleaseBranchStatus.MDEPS_ACTUAL) {
			// need to build
			if (actionKind == ActionKind.FORK) {
				return new ActionNone(comp, childActions, "nothing to fork");
			}
			return new SCMActionBuild(comp, childActions, ReleaseReason.NEW_FEATURES, rb.getVersion(), options);
		}

		if (hasForkChildActions(childActions)) {
			skipAllBuilds(childActions);
			if (actionKind == ActionKind.FORK) {
				return new ActionNone(comp, childActions, "nothing to build");
			}
			return new SCMActionForkReleaseBranch(comp, childActions, ReleaseReason.NEW_DEPENDENCIES, options);
		}

		return new ActionNone(comp, childActions, rbs.toString());
	}

	private boolean needToActualizeMDeps(List<IAction> childActions, ReleaseBranch currentCompRB) {
		List<Component> mDeps = currentCompRB.getMDeps();
		ReleaseBranch mDepRB;
		for (Component mDep : mDeps) {
			mDepRB = new ReleaseBranch(mDep, repos);
			if (mDepRB.getStatus() == ReleaseBranchStatus.MDEPS_ACTUAL) {
				if (!mDepRB.getCurrentVersion().equals(mDep.getVersion())) {
					return true;
				}
			} else {
				if (needToActualizeMDeps(childActions, mDepRB)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasForkChildActions(List<IAction> childActions) {
		for (IAction action : childActions) {
			if (action instanceof SCMActionForkReleaseBranch) {
				return true;
			}
		}
		return false;
	}

	private void skipAllForks(List<IAction> childActions) {
		ListIterator<IAction> li = childActions.listIterator();
		IAction action;
		while (li.hasNext()) {
			action = li.next();
			if (action instanceof SCMActionForkReleaseBranch) {
				li.set(new ActionNone(((SCMActionForkReleaseBranch) action).getComponent(), action.getChildActions(), "fork skipped because not all parent components built"));
			}
		}
	}

	private void skipAllBuilds(List<IAction> childActions) {
		ListIterator<IAction> li = childActions.listIterator();
		IAction action;
		while (li.hasNext()) {
			action = li.next();
			if (action instanceof SCMActionBuild) {
				li.set(new ActionNone(((SCMActionBuild) action).getComponent(), action.getChildActions(), "build skipped because not all parent components forked"));
			}
		}
	}
	
	public IAction getTagReleaseAction(Component comp) {
		List<IAction> childActions = new ArrayList<>();
		DevelopBranch db = new DevelopBranch(comp);
		List<Component> mDeps = db.getMDeps();

		for (Component mDep : mDeps) {
			childActions.add(getTagReleaseAction(mDep));
		}
		return getTagReleaseActionRoot(comp, childActions);
	}

	public IAction getTagReleaseAction(String compName) {
		return getTagReleaseAction(new Component(compName, repos));
	}

	private IAction getTagReleaseActionRoot(Component comp, List<IAction> childActions) {
		ReleaseBranch rb = new ReleaseBranch(comp, repos);
		CommitsFile cf = new CommitsFile();
		IVCS vcs = comp.getVCS();
		
		String delayedRevisionToTag = cf.getRevisitonByUrl(comp.getVcsRepository().getUrl());
		
		if (delayedRevisionToTag == null) {
			return new ActionNone(comp, childActions, "no delayed tags");
		}
		
		List<VCSTag> tagsOnRevision = vcs.getTagsOnRevision(delayedRevisionToTag);
		if (tagsOnRevision.isEmpty()) {
			return new SCMActionTagRelease(comp, childActions, "tag message", options);
		}
		Version delayedTagVersion = new Version(vcs.getFileContent(rb.getReleaseBranchName(), SCMWorkflow.VER_FILE_NAME, delayedRevisionToTag));
		for (VCSTag tag : tagsOnRevision) {
			if (tag.getTagName().equals(delayedTagVersion.toReleaseString())) {
				return new ActionNone(comp, childActions, "tag " + tag.getTagName() + " already exists");
			}
		}
		return new SCMActionTagRelease(comp, childActions, "tag message", options);
	}
}
