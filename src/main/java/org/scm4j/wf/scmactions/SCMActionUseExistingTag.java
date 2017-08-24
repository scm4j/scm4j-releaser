package org.scm4j.wf.scmactions;

import java.util.List;

import org.scm4j.commons.progress.IProgress;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.wf.actions.ActionAbstract;
import org.scm4j.wf.actions.IAction;
import org.scm4j.wf.conf.Component;
import org.scm4j.wf.conf.Option;

public class SCMActionUseExistingTag extends ActionAbstract {
	
	private final VCSTag tag;

	public SCMActionUseExistingTag(Component dep, List<IAction> childActions, VCSTag tag, List<Option> options) {
		super(dep, childActions, options);
		this.tag = tag;
	}
	
	public VCSTag getTag() {
		return tag;
	}
	
	@Override
	public String toString() {
		return "using existing tag for " + comp.getCoords().toString() + ": " + tag.toString();
	}

	@Override
	public void execute(IProgress progress) {
		for (IAction action : childActions) {
			try (IProgress nestedProgress = progress.createNestedProgress(action.toString())) {
				action.execute(nestedProgress);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} 
		}
		progress.reportStatus(toString());
	}
}
