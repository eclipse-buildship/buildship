package org.eclipse.buildship.ui.editor;

import org.eclipse.buildship.ui.PluginImage;
import org.eclipse.buildship.ui.PluginImages;
import org.eclipse.buildship.ui.UiPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.EditorActionBarContributor;

public class GradleEditorContributor extends EditorActionBarContributor {
	private static final String REFRESHPROJECT_COMMAND_ID = "org.eclipse.buildship.ui.commands.refreshproject";

	@Override
	public void contributeToToolBar(IToolBarManager toolBarManager) {
		toolBarManager.add(new Action("Refresh Gradle Project", PluginImages.REFRESH.withState(PluginImage.ImageState.ENABLED).getImageDescriptor()) {
			@Override
			public void runWithEvent(Event event) {
				IHandlerService service=getActionBars().getServiceLocator().getService(IHandlerService.class);
				try {
					service.executeCommand(REFRESHPROJECT_COMMAND_ID, event);
				} catch (Exception e) {
					UiPlugin.logger().error("Failed to invoke command "+REFRESHPROJECT_COMMAND_ID, e);
				}
			}
		});
	}
}
