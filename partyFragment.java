package kz.uco.esbd.web.screens.underwritingapplications;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.EntityStates;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.UiComponents;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.executors.BackgroundTask;
import com.haulmont.cuba.gui.executors.BackgroundWorker;
import com.haulmont.cuba.gui.executors.TaskLifeCycle;
import com.haulmont.cuba.gui.model.*;
import com.haulmont.cuba.gui.screen.*;
import https.icweb.iicwebservice.Client;
import kz.uco.base.common.PhoneValidateAndFormatUiHelper;
import kz.uco.base.common.enums.PartyType;
import kz.uco.esbd.entity.*;
import kz.uco.esbd.service.BaselCommonService;
import kz.uco.esbd.web.common.PartyHelper;
import kz.uco.esbd.web.screens.underwritingapplications.bean.UnderwritingApplicationCreationStagePanel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static kz.uco.base.common.enums.PartyType.COMPANY;
import static kz.uco.base.common.enums.PartyType.CONTACT;

@UiController("esbd_ApplicationPartyFragment")
@UiDescriptor("application-party-fragment.xml")
public class ApplicationPartyFragment extends ScreenFragment {

    protected PartyKind partyKind;
    protected PartyBasel party;
    protected String initialNationalIdentifier;
    protected int naturalPersonInt;
    protected int residentInt;
    protected List<PartyBasel> partyBasels = new ArrayList<>();

    @Inject
    protected CollectionContainer<PartyBasel> partyBaselsDc;
    @Inject
    protected InstanceContainer<PartyBasel> partyDc;
    @Inject
    protected InstancePropertyContainer<ContactBasel> contactDc;
    @Inject
    protected InstancePropertyContainer<CompanyBasel> companyDc;
    @Inject
    protected InstancePropertyContainer<Document> documentDc;
    @Inject
    protected CollectionLoader<DicGovernmentOrganization> dicGovernmentOrganizationDl;
    @Inject
    protected CollectionLoader<DicDocumentType> dicDocumentTypeDl;
    @Inject
    protected CollectionLoader<DicEconomicActivity> dicEconomicActivityDl;
    @Inject
    protected CollectionLoader<DicEconomicSector> dicEconomicSectorDl;
    @Inject
    protected InstanceLoader<PartyBasel> partyDl;
    
    @Inject
    protected GroupTable<PartyBasel> partyBaselsTableContactType;
    @Inject
    protected GroupTable<PartyBasel> partyBaselsTableCompanyType;
    @Inject
    protected VBoxLayout contactBox;
    @Inject
    protected VBoxLayout companyBox;
    @Inject
    protected VBoxLayout insurancePolicyAttributesVBox;
    @Inject
    protected VBoxLayout searchEsbdVbox;
    @Inject
    protected HBoxLayout editActions;

    @Inject
    protected CheckBox isInsuredCheckBox;
    @Inject
    protected CheckBox isBeneficiaryCheckBox;
    @Inject
    protected CheckBox isLegallyAuthorizedPersonsToOperateVehicleCheckBox;
    @Inject
    protected CheckBox residentField;
    @Inject
    protected CheckBox isAffiliatedPersonField;

    @Inject
    protected LookupField<DicDocumentType> documentTypeField;
    @Inject
    protected LookupField<DicGovernmentOrganization> issuedByField;
    @Inject
    protected LookupField<DicEconomicSector> economicSectorField;
    @Inject
    protected LookupField<DicEconomicActivity> economicActivityField;

    @Inject
    protected RadioButtonGroup<PartyType> partyTypeField;

    @Inject
    protected TextField<String> phoneNumberField;
    @Inject
    protected TextField<String> organizationBinField;
    @Inject
    protected TextField<String> companyNameField;
    @Inject
    protected TextField<String> nationalIdentifierField;
    @Inject
    protected TextField<String> firstNameField;
    @Inject
    protected TextField<String> lastNameField;
    @Inject
    protected TextField<String> middleNameField;
    @Inject
    protected TextField<String> documentNumberField;
    @Inject
    protected TextField<String> addressField;
    @Inject
    protected Label<String> partyKindLabelField;

    @Inject
    protected DateField<Date> issuedDateField;
    @Inject
    protected DateField<Date> dateOfBirthField;

    @Inject
    protected Button windowCommitAndCloseBtn;
    @Inject
    protected Button windowCommitBtn;
    @Inject
    protected Button windowCloseBtn;
    @Inject
    protected Button searchButton;

    //Cuba beans
    @Inject
    protected DataManager dataManager;
    @Inject
    protected EntityStates entityStates;
    @Inject
    protected ProgressBar searchProgressBar;
    @Inject
    protected BackgroundWorker backgroundWorker;
    @Inject
    protected UiComponents uiComponents;
    @Inject
    protected Messages messages;
    @Inject
    protected Notifications notifications;
    
    //Uco beans
    @Inject
    protected PartyHelper partyHelper;
    @Inject
    protected BaselCommonService baselCommonService;


    public ApplicationPartyFragment init(PartyBasel party, PartyKind kind) {
        this.party = party;
        this.partyKind = kind;

        fillInitialNationalIdentifier();
        loadBeforeShow();
        return this;
    }

    protected void fillInitialNationalIdentifier() {
        if (party.getPartyType() == CONTACT) {
            initialNationalIdentifier = party.getNationalIdentifier();
        } else {
            initialNationalIdentifier = party.getCompany() == null ? null : party.getCompany().getOrganizationBin();
        }
    }

    protected void loadBeforeShow() {
        partyDc.setItem(party);
        dicGovernmentOrganizationDl.load();
        dicDocumentTypeDl.load();
        dicEconomicActivityDl.load();
        dicEconomicSectorDl.load();
        partyKindLabelField.setValue(messages.getMessage(partyKind));
        setResidentFieldValue();

        if (partyKind == PartyKind.INSURANT) {
            insurancePolicyAttributesVBox.setVisible(true);
        }
    }

    protected void setResidentFieldValue() {
        if (party != null && entityStates.isNew(party)) {
            party.setResident(true);
        }
    }

    @Subscribe("partyTypeField")
    public void onPartyTypeFieldValueChange(HasValue.ValueChangeEvent<PartyType> event) {
        updateUiByPartyType(event);
        updateUiByResident();
    }

    @Subscribe("residentField")
    protected void onResidentFieldValueChange(HasValue.ValueChangeEvent<Boolean> event) {
        updateUiByResident();
    }

    protected void updateUiByPartyType(HasValue.ValueChangeEvent<PartyType> event) {
        boolean isCompany = COMPANY == event.getValue();

        companyBox.setVisible(isCompany);
        contactBox.setVisible(!isCompany);
        isLegallyAuthorizedPersonsToOperateVehicleCheckBox.setVisible(!isCompany);

        partyBaselsTableCompanyType.setVisible(isCompany);
        partyBaselsTableContactType.setVisible(!isCompany);

        naturalPersonInt = party.getPartyType() == CONTACT ? 1 : 0;
    }

    /**
     * Когда резидент true and физ лицо
     */
    protected void updateUiByResident() {
        boolean isResident = Boolean.TRUE.equals(party.getResident());
        residentInt = Boolean.TRUE.equals(isResident) ? 1 : 0;

        nationalIdentifierField.setRequired(isResident);
        searchEsbdVbox.setVisible(!isResident);

        if (party.getPartyType() == CONTACT) {
            dateOfBirthField.setRequired(!isResident);
            dateOfBirthField.setVisible(!isResident);
        } else {
            organizationBinField.setRequired(isResident);
        }
    }

    @Subscribe("phoneNumberField")
    public void onPhoneNumberFieldValueChange(HasValue.ValueChangeEvent<String> event) {
        try {
            AppBeans.get(PhoneValidateAndFormatUiHelper.class).validateAndFormatPhoneNumber(event);
        } catch (RuntimeException runtimeException) {
            if (Objects.equals(
                    runtimeException.getMessage(),
                    messages.getMainMessage("message.phoneNumberIsIncorrect"))) {
                notifications.create(Notifications.NotificationType.TRAY)
                        .withCaption(messages.getMainMessage("message.phoneNumberIsIncorrect")).show();
                phoneNumberField.clear();
            }
        }
    }

    @Subscribe("nationalIdentifierField")
    protected void onNationalIdentifierFieldValueChange(HasValue.ValueChangeEvent<String> event) {
        String iinText = event.getValue();

        searchAndFillByNationalIdentifier(iinText);
    }

    @Subscribe("organizationBinField")
    protected void onOrganizationBinFieldValueChange(HasValue.ValueChangeEvent<String> event) {
        String binText = event.getValue();

        searchAndFillByNationalIdentifier(binText);
    }

    protected void searchAndFillByNationalIdentifier(String nationalIdentifier) {
        if (StringUtils.isNotBlank(nationalIdentifier)
                && nationalIdentifier.length() == 12
                && !nationalIdentifier.equals(initialNationalIdentifier)) {
            fillInitialNationalIdentifier();

            try {
                Client clientFromEsbd = partyHelper
                        .getClientByIdentifierFromEsbd(nationalIdentifier, naturalPersonInt, residentInt);

                partyHelper.fillApplicationPartyFromEsbd(party, clientFromEsbd);
            } catch (IllegalArgumentException e) {
                notifications.create(Notifications.NotificationType.TRAY)
                        .withCaption(baselCommonService.getExceptionMessage(e))
                        .show();
            } catch (Exception e) {
                notifications.create(Notifications.NotificationType.ERROR)
                        .withCaption(baselCommonService.getExceptionMessage(e))
                        .show();
            }
        }
    }

    @Subscribe("searchButton")
    protected void onSearchButtonClick(Button.ClickEvent event) {
        String lastNameFieldValue = lastNameField.getValue();
        String firstNameFieldValue = firstNameField.getValue();
        String middleNameFieldValue = middleNameField.getValue();
        Date birthFieldValue = dateOfBirthField.getValue();
        String companyNameFieldValue = companyNameField.getValue();

        if ((StringUtils.isAnyBlank(lastNameFieldValue, firstNameFieldValue) || birthFieldValue == null)
                &&
                naturalPersonInt == 1
        ) {
            notifications.create(Notifications.NotificationType.TRAY)
                    .withCaption("Введите ФИО и дату рождения")
                    .show();
            return;
        } else if (StringUtils.isBlank(companyNameFieldValue) && naturalPersonInt == 0) {
            notifications.create(Notifications.NotificationType.TRAY)
                    .withCaption("Введите наименование компании и попробуйте еще раз")
                    .show();
        }

        BackgroundTask<Integer, Void> task = searchPartyBaselsFromEsbdInBackground(
                lastNameFieldValue,
                firstNameFieldValue,
                middleNameFieldValue,
                birthFieldValue,
                companyNameFieldValue);

        searchProgressBar.setVisible(true);
        searchButton.setEnabled(false);
        backgroundWorker.handle(task).execute();
    }

    @Nullable
    private BackgroundTask<Integer, Void> searchPartyBaselsFromEsbdInBackground(@Nonnull String lastNameFieldValue,
                                                                                @Nonnull String firstNameFieldValue,
                                                                                @Nullable String middleNameFieldValue,
                                                                                @Nonnull Date birthFieldValue,
                                                                                @Nonnull String companyName) {
        return new BackgroundTask<Integer, Void>(100, TimeUnit.SECONDS) {
            @Override
            public Void run(TaskLifeCycle<Integer> taskLifeCycle) {
                // This code will be executed in a background thread
                partyBasels = partyHelper.getPartyBaselsFromClient(
                        firstNameFieldValue,
                        lastNameFieldValue,
                        middleNameFieldValue,
                        birthFieldValue,
                        residentInt,
                        naturalPersonInt,
                        companyName);

                return null;
            }

            @Override
            public boolean handleException(Exception ex) {
                searchButton.setEnabled(true);
                searchProgressBar.setVisible(false);
                return super.handleException(ex);
            }

            @Override
            public void done(Void result) {
                // This code will be executed in the UI thread when the task is done
                searchButton.setEnabled(true);
                searchProgressBar.setVisible(false);

                if (partyBasels.isEmpty()) {
                    notifications.create(Notifications.NotificationType.TRAY)
                            .withCaption("Клиент не найден")
                            .show();
                } else if (partyBasels.size() == 1) {
                    fillUiApplicationPartyFromEsbd(partyBasels.get(0).getEsbdClient());
                } else {
                    partyBaselsDc.getMutableItems().addAll(partyBasels);
                }
            }

            @Override
            public void progress(List<Integer> changes) {
                // This code will be executed in the UI thread when the progress changes
                int lastProgress = changes.get(changes.size() - 1);
                searchProgressBar.setValue((double) lastProgress / 10);
            }
        };
    }

    protected void selectedBaselContact(PartyBasel partyBasel) {
        Client esbdClient = null;

        for (PartyBasel basel : partyBasels) {
            if (basel.equals(partyBasel)) {
                esbdClient = basel.getEsbdClient();
                break;
            }
        }
        fillUiApplicationPartyFromEsbd(esbdClient);
    }

    protected void fillUiApplicationPartyFromEsbd(Client esbdClient) {
        try {
            partyHelper.fillApplicationPartyFromEsbd(party, esbdClient);
        } catch (IllegalArgumentException e) {
            notifications.create(Notifications.NotificationType.TRAY)
                    .withCaption(baselCommonService.getExceptionMessage(e))
                    .show();
        } catch (Exception e) {
            notifications.create(Notifications.NotificationType.ERROR)
                    .withCaption(baselCommonService.getExceptionMessage(e))
                    .show();
        }
    }

    public Map<String, String> getValueMapForBilletData() {
        Map<String, String> valuesMap = new LinkedHashMap<>();

        if (COMPANY == this.partyTypeField.getValue()) {
            valuesMap.put(
                    messages.getMessage(this.getClass(), "np.bin"),
                    this.organizationBinField.getValue());
            valuesMap.put(
                    messages.getMessage(this.getClass(), "np.companyName"),
                    this.companyNameField.getValue());
        } else {
            String firstName = this.firstNameField.getValue();
            String lastName = this.lastNameField.getValue();
            String name = (firstName != null && lastName != null) ? firstName + " " + lastName : "";
            valuesMap.put(
                    messages.getMessage(this.getClass(), "np.iin"),
                    this.nationalIdentifierField.getValue());
            valuesMap.put(
                    messages.getMessage(this.getClass(), "np.fullName"),
                    name);
        }
        return valuesMap;
    }

    public void focus() {
        residentField.focus();
    }

    @Subscribe("windowCommitBtn")
    protected void onWindowCommitBtnClick(Button.ClickEvent event) {
        IUnderwritingApplicationFragment host = (IUnderwritingApplicationFragment) getHostController();
        host.getUnderwritingApplicationEditor().commit();
        UnderwritingApplicationCreationStagePanel stagePanel = host.getUnderwritingApplicationEditor().getStagePanel();
        if (stagePanel.getCurrentBillet() != null
        ) {
            Application application = host.getUnderwritingApplicationEditor().getUnderwritingApplication();
            PartyBasel insurant = application.getInsurant();

            UnderwritingApplicationCreationStage stage = stagePanel.getCurrentBillet().getStage();
            if (host.getStageBillet().getStage() == stage) {
                if (insurant != null && stage == UnderwritingApplicationCreationStage.INSURANT) {
                    //Если Является Выгодоприобретателем или Лица, допущенные к эксплуатации ТС на законном основании.

                    //switch
                    if (party.getIsInsured() || party.getIsLegallyAuthorizedPersonsToOperateVehicle()) {
                        if (Boolean.TRUE.equals(party.getIsBeneficiary())) {
                            stagePanel.switchStage(UnderwritingApplicationCreationStage.INSURANCE_OBJECTS);
                        } else {
                            stagePanel.switchStage(UnderwritingApplicationCreationStage.BENEFICIARY);
                        }
                    } else {
                        stagePanel.nextStage();
                    }
                } else {
                    if (Boolean.TRUE.equals(party.getIsBeneficiary())) {
                        stagePanel.switchStage(UnderwritingApplicationCreationStage.INSURANCE_OBJECTS);
                    }
                    stagePanel.nextStage();
                }
            }
        }
    }

    public Collection<Action> getEditorScreenDefaultActions() {
        List<Button> components = new ArrayList<>();
        components.add(windowCloseBtn);
        components.add(windowCloseBtn);
        components.add(windowCommitAndCloseBtn);
        return components.stream().map(ActionOwner::getAction).collect(Collectors.toList());
    }

    protected PartyBasel setUpParty(PartyBasel applicationParty) {
        party = partyHelper.setPartyType(
                applicationParty,
                partyKind, partyTypeField.getValue(),
                companyNameField.getValue(), organizationBinField.getValue(),
                firstNameField.getValue(), lastNameField.getValue(),
                middleNameField.getValue(), nationalIdentifierField.getValue(),
                documentNumberField.getValue(), issuedDateField.getValue(),
                documentTypeField.getValue(), issuedByField.getValue(),
                economicSectorField.getValue(), economicActivityField.getValue(),
                phoneNumberField.getValue(), addressField.getValue()
        );

        party.setResident(residentField.getValue());
        party.setIsAffiliatedPerson(isAffiliatedPersonField.getValue());

        if (party.getContact() != null) {
            party.getContact().setDateOfBirth(dateOfBirthField.getValue());
        }

        party.setIsInsured(isInsuredCheckBox.getValue());
        party.setIsBeneficiary(isBeneficiaryCheckBox.getValue());
        party.setIsLegallyAuthorizedPersonsToOperateVehicle(isLegallyAuthorizedPersonsToOperateVehicleCheckBox.getValue());
        return party;
    }

    @Install(to = "partyBaselsTableCompanyType.select", subject = "columnGenerator")
    protected Component partyBaselsTableCompanyTypeSelectColumnGenerator(PartyBasel partyBasel) {
        return selectButtonComponent(partyBasel);
    }

    @Install(to = "partyBaselsTableContactType.select", subject = "columnGenerator")
    protected Component partyBaselsTableContactTypeSelectColumnGenerator(PartyBasel partyBasel) {
        return selectButtonComponent(partyBasel);
    }

    protected Button selectButtonComponent(PartyBasel partyBasel) {
        Button button = uiComponents.create(Button.class);
        button.setCaption(messages.getMessage(this.getClass(), "selectButtonCaption"));
        button.addClickListener(clickEvent ->
                selectedBaselContact(partyBasel)
        );
        return button;
    }

}
