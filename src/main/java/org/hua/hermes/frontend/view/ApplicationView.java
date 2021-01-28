package org.hua.hermes.frontend.view;

import com.vaadin.componentfactory.enhancedcrud.*;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.*;
import de.codecamp.vaadin.security.spring.access.SecuredAccess;
import org.hua.hermes.backend.entity.Application;
import org.hua.hermes.backend.entity.ApplicationState;
import org.hua.hermes.frontend.component.StatusBadge;
import org.hua.hermes.frontend.constant.CrudConstants;
import org.hua.hermes.frontend.repository.impl.ApplicationRepositoryImpl;
import org.hua.hermes.frontend.util.TemplateUtil;
import org.hua.hermes.frontend.util.style.css.lumo.BadgeColor;
import org.hua.hermes.frontend.util.style.css.lumo.BadgeShape;
import org.hua.hermes.frontend.util.style.css.lumo.BadgeSize;
import org.hua.hermes.frontend.view.presenter.ApplicationsCrudPresenter;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;
import java.util.List;
import static org.hua.hermes.frontend.constant.DateTimeConstants.DATETIME_FORMATTER;

@Route("applications")
@SecuredAccess("hasRole('ROLE_CITIZEN')")
public class ApplicationView extends Crud<Application>
        implements HasNotifications, HasUrlParameter<String>, HasStyle {

    private final ApplicationsCrudPresenter presenter;

    public ApplicationView(@Autowired ApplicationRepositoryImpl repository) {
        super(Application.class, new Grid<>(),createApplicationEditor());

        presenter = new ApplicationsCrudPresenter(repository);
        presenter.setView(this);

        this.setEditorPosition(CrudEditorPosition.ASIDE);
        this.getGrid().setSelectionMode(Grid.SelectionMode.SINGLE);

        this.getGrid().setDataProvider(DataProvider.fromCallbacks(
                fetch -> presenter.findAll(fetch.getOffset(),fetch.getLimit()).stream(),
                count -> presenter.count()));

        this.getDelete().setEnabled(false);
        this.getDelete().getStyle().set("visibility","hidden");

        setupGrid(getGrid());
        setI18n(setupI18n());
        setupEventListeners();
        setSizeFull();
    }

    public CrudI18n setupI18n()
    {
        CrudI18n crudI18n = CrudI18n.createDefault();
        crudI18n.setNewItem("New " + Application.ENTITY_NAME);
        crudI18n.setEditItem("Edit " + Application.ENTITY_NAME);
        crudI18n.setEditLabel("Edit" + Application.ENTITY_NAME);
        crudI18n.getConfirm().getCancel().setContent(CrudConstants.DISCARD_MESSAGE);
        crudI18n.getConfirm().getDelete().setContent(String.format(CrudConstants.DELETE_MESSAGE, "Application"));
        crudI18n.setDeleteItem("Delete");
        return crudI18n;
    }

    public void setupEventListeners() {

        //Handling only update
        this.addSaveListener(e -> {
            var application = e.getItem();
            if (!presenter.update(application)) {
                this.cancelSave();
            }
        });

        this.addEditListener(e -> {
            navigateToApplication(e.getItem().getId());
            this.getEditor().setItem(e.getItem());
        });

        this.addCancelListener(e -> navigateToApplication(null));
    }

    private void navigateToApplication(String id) {
        getUI().ifPresent(ui -> ui.navigate(TemplateUtil.generateLocation("/applications", id)));
    }

    protected void setupGrid(Grid<Application> grid)
    {
        grid.addColumn(Application::getId).setHeader(Application.ID_LABEL);
        grid.addColumn(Application::getCreatedBy).setHeader(Application.CREATED_BY_LABEL);
        grid.addColumn((application) -> DATETIME_FORMATTER.format(application.getCreatedDate())).setHeader(Application.CREATED_DATE_LABEL);
        grid.addColumn(Application::getLastModifiedBy).setHeader(Application.LAST_MODIFIED_BY_LABEL);
        grid.addColumn((application) -> DATETIME_FORMATTER.format(application.getLastModifiedDate())).setHeader(Application.LAST_MODIFIED_ON_LABEL);

        grid.addComponentColumn(application -> {
            BadgeColor color;
            String status;
            switch (application.getState()){
                case APPROVED:
                case SUBMITTED:
                case COMPLETED:
                    color = BadgeColor.SUCCESS_PRIMARY;
                    break;
                case CANCELED:
                case REJECTED:
                    color = BadgeColor.ERROR_PRIMARY;
                    break;
                case RESUBMISSION_REQUIRED:
                    color = BadgeColor.CONTRAST_PRIMARY;
                    break;
                default:
                    throw new IllegalStateException("Illegal application state");
            }
            status = application.getState().getName();
            var badge = new StatusBadge(status, color, BadgeSize.M, BadgeShape.PILL);
            badge.getElement().setProperty("title", status);
            return badge;
        }).setHeader(Application.STATE_LABEL);

        grid.addColumn((application) -> DATETIME_FORMATTER.format(application.getAppointmentDate())).setHeader(Application.APPOINTMENT_DATE_LABEL);

        grid.setItemDetailsRenderer(new ComponentRenderer<>(item -> {
            var label = new Label("Details:");
            var text = new Label(item.getDetails());
            text.getStyle().set("font-weight", "bold");
            return new HorizontalLayout(label,text);
        }));

        grid.getColumns().forEach(column -> column.setResizable(true).setAutoWidth(true));

        addEditColumn(grid);
    }

    private static CrudEditor<Application> createApplicationEditor() {

        Select<ApplicationState> select = new Select<>();
        select.setLabel(Application.STATE_LABEL);
        List<ApplicationState> applicationStates = Arrays.asList(ApplicationState.values());

        // Choose which property from Application State is the presentation value
        select.setItemLabelGenerator(ApplicationState::getName);
        select.setItems(applicationStates);

        FormLayout layout = new FormLayout(select);
        var binder = new Binder<>(Application.class);

        binder.forField(select).bind(Application::getState,Application::setState);

        return new BinderCrudEditor<>(binder, layout);
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String id)
    {
        //Our back-end will throw a 403 if someone tries to access an application of an another organization
        //So this scenario is already covered.
        //It is different than keycloak's cases where we needed to manually validate in which organization the user works.

        if (id != null) {
            var application = getEditor().getItem();
            if (application != null && id.equals(application.getId())) {
                return;
            }
            application = presenter.findById(id).orElseThrow(NotFoundException::new);
            edit(application, EditMode.EXISTING_ITEM);
        } else {
            setOpened(false);
        }
    }
}
