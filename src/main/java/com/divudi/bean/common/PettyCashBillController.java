/*
 * To change this currentlate, choose Tools | currentlates
 * and open the currentlate in the editor.
 */
package com.divudi.bean.common;

import com.divudi.bean.memberShip.PaymentSchemeController;
import com.divudi.data.BillClassType;
import com.divudi.data.BillNumberSuffix;
import com.divudi.data.BillType;
import com.divudi.data.PaymentMethod;
import com.divudi.data.Title;
import com.divudi.data.dataStructure.PaymentMethodData;
import com.divudi.ejb.BillNumberGenerator;
import com.divudi.ejb.CashTransactionBean;
import com.divudi.entity.Bill;
import com.divudi.entity.BillItem;
import com.divudi.entity.BilledBill;
import com.divudi.entity.Person;
import com.divudi.entity.WebUser;
import com.divudi.facade.BillFacade;
import com.divudi.facade.BillItemFacade;
import com.divudi.facade.PersonFacade;
import com.divudi.facade.util.JsfUtil;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.TemporalType;
import org.primefaces.event.TabChangeEvent;

/**
 *
 * @author safrin
 */
@Named
@SessionScoped
public class PettyCashBillController implements Serializable {

    private Bill current;
    private boolean printPreview = false;
    @EJB
    private BillNumberGenerator billNumberBean;
    @Inject
    private SessionController sessionController;
    @EJB
    private BillFacade billFacade;
    @EJB
    private BillItemFacade billItemFacade;
    private Person newPerson;
    PaymentMethodData paymentMethodData;
    String comment;

    public PaymentMethodData getPaymentMethodData() {
        if (paymentMethodData == null) {
            paymentMethodData = new PaymentMethodData();
        }
        return paymentMethodData;
    }

    public void setPaymentMethodData(PaymentMethodData paymentMethodData) {
        this.paymentMethodData = paymentMethodData;
    }

    public PettyCashBillController() {
    }

    public Title[] getTitle() {
        return Title.values();
    }

    @Inject
    PaymentSchemeController paymentSchemeController;

    public PaymentSchemeController getPaymentSchemeController() {
        return paymentSchemeController;
    }

    public void setPaymentSchemeController(PaymentSchemeController paymentSchemeController) {
        this.paymentSchemeController = paymentSchemeController;
    }

    private boolean errorCheck() {
        if (getCurrent().getPaymentMethod() == null) {
            return true;
        }

//        //Edited 2014.10.04 p
//        if (getCurrent().getStaff() == null && getCurrent().getPerson() == null && getNewPerson() == null) {
//            UtilityController.addErrorMessage("Can't settle without Person");
//            return true;
//        }
        if (getCurrent().getPaymentMethod() != null && getCurrent().getPaymentMethod() == PaymentMethod.Cheque) {
            if (getCurrent().getBank() == null || getCurrent().getChequeRefNo() == null || getCurrent().getChequeDate() == null) {
                UtilityController.addErrorMessage("Please select Cheque Number,Bank and Cheque Date");
                return true;
            }

        }

        if (getPaymentSchemeController().errorCheckPaymentMethod(getCurrent().getPaymentMethod(), paymentMethodData)) {
            return true;
        }

        if (getCurrent().getNetTotal() < 1) {
            UtilityController.addErrorMessage("Type Amount");
            return true;
        }

        if (checkInvoice()) {
            UtilityController.addErrorMessage("Invoice Number Already Exist");
            return true;
        }

        return false;
    }

    private boolean checkInvoice() {
        Calendar year = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.set(year.get(Calendar.YEAR), 3, 1, 0, 0, 0);
        Date fd = c.getTime();
        System.out.println("d = " + fd);
        String inv = createInvoiceNumberSuffix() + getCurrent().getIntInvoiceNumber();
        System.out.println("inv = " + inv);
        String sql = "Select b From BilledBill b where "
                + " b.retired=false "
                + " and b.cancelled=false "
                + " and b.billType= :btp "
                + " and b.createdAt > :fd "
                + " and upper(b.invoiceNumber) like '%" + inv.trim().toUpperCase() + "%'";
        HashMap h = new HashMap();
        h.put("btp", BillType.PettyCash);
        h.put("fd", fd);
        System.out.println("h = " + h);
        System.out.println("sql = " + sql);
        List<Bill> tmp = getBillFacade().findBySQL(sql, h, TemporalType.TIMESTAMP);

        System.out.println("tmp.size() =" + tmp.size());
        if (tmp.size() > 0) {
            System.out.println("tmp.get(0).getInsId() =" + tmp.get(0).getInsId());
            return true;
        }

        return false;
    }

    private String createInvoiceNumberSuffix() {
        
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        String s1 = y + "";
        String s2 = y + 1 + "";
        String s = s1.substring(2, 4) + s2.substring(2, 4) + "-";
        System.out.println("s = " + s);

        return s;
    }

    @Inject
    BillBeanController billBean;

    public BillBeanController getBillBean() {
        return billBean;
    }

    public void setBillBean(BillBeanController billBean) {
        this.billBean = billBean;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    private void saveBill() {

        getCurrent().setInsId(getBillNumberBean().institutionBillNumberGenerator(getSessionController().getInstitution(), BillType.PettyCash, BillClassType.BilledBill, BillNumberSuffix.PTYPAY));
        getCurrent().setDeptId(getBillNumberBean().institutionBillNumberGenerator(getSessionController().getDepartment(), BillType.PettyCash, BillClassType.BilledBill, BillNumberSuffix.PTYPAY));

        getCurrent().setBillType(BillType.PettyCash);

        getCurrent().setDepartment(getSessionController().getDepartment());
        getCurrent().setInstitution(getSessionController().getInstitution());
        getCurrent().setComments(comment);

        getCurrent().setBillDate(new Date());
        getCurrent().setBillTime(new Date());

        getCurrent().setCreatedAt(new Date());
        getCurrent().setCreater(getSessionController().getLoggedUser());

        getCurrent().setTotal(0 - getCurrent().getNetTotal());
        getCurrent().setNetTotal(0 - getCurrent().getNetTotal());

        getBillBean().setPaymentMethodData(getCurrent(), getCurrent().getPaymentMethod(), getPaymentMethodData());

        getBillFacade().create(getCurrent());
    }
    @EJB
    private PersonFacade personFacade;
    @EJB
    private CashTransactionBean cashTransactionBean;

    public void settleBill() {

        if (errorCheck()) {
            return;
        }

        switch (getTabId()) {
            case "tabStaff":
                if (current.getStaff() == null) {
                    JsfUtil.addErrorMessage("Staff?");
                    return;
                }
                break;
            case "tabSearchPerson":
                if (current.getPerson() == null) {
                    JsfUtil.addErrorMessage("Person?");
                    return;
                }
                break;
            case "tabNew":
                if (getNewPerson().getName().trim().equals("")) {
                    JsfUtil.addErrorMessage("Person?");
                    return;
                }
                break;
            default:
                JsfUtil.addErrorMessage(getTabId());
                return;
        }

        if (getTabId().equals("tabNew")) {
            getPersonFacade().create(getNewPerson());
            getCurrent().setPerson(getNewPerson());
        }

        getCurrent().setTotal(getCurrent().getNetTotal());
        getCurrent().setInvoiceNumber(createInvoiceNumberSuffix() + getCurrent().getIntInvoiceNumber());

        saveBill();
        saveBillItem();

        WebUser wb = getCashTransactionBean().saveBillCashOutTransaction(getCurrent(), getSessionController().getLoggedUser());
        getSessionController().setLoggedUser(wb);
        UtilityController.addSuccessMessage("Bill Saved");
        printPreview = true;
        System.out.println("getCurrent().getInvoiceNumber() = " + getCurrent().getInvoiceNumber());

    }

    private void saveBillItem() {
        BillItem tmp = new BillItem();

        tmp.setCreatedAt(new Date());
        tmp.setCreater(getSessionController().getLoggedUser());
        tmp.setBill(getCurrent());
        tmp.setNetValue(0 - getCurrent().getNetTotal());
        getBillItemFacade().create(tmp);

    }
    private String tabId = "tabStaff";

    public void recreateModel() {
        current = null;
        printPreview = false;
        newPerson = null;
        comment = null;

        tabId = "tabStaff";
    }

    public void onTabChange(TabChangeEvent event) {
        setTabId(event.getTab().getId());
    }

    public void prepareNewBill() {
        recreateModel();
    }

    public Bill getCurrent() {
        if (current == null) {
            current = new BilledBill();

        }
        return current;
    }

    public void setCurrent(Bill current) {
        this.current = current;
    }

    public boolean isPrintPreview() {
        return printPreview;
    }

    public void setPrintPreview(boolean printPreview) {
        this.printPreview = printPreview;
    }

    public BillNumberGenerator getBillNumberBean() {
        return billNumberBean;
    }

    public void setBillNumberBean(BillNumberGenerator billNumberBean) {
        this.billNumberBean = billNumberBean;
    }

    public SessionController getSessionController() {
        return sessionController;
    }

    public void setSessionController(SessionController sessionController) {
        this.sessionController = sessionController;
    }

    public BillFacade getBillFacade() {
        return billFacade;
    }

    public void setBillFacade(BillFacade billFacade) {
        this.billFacade = billFacade;
    }

    public BillItemFacade getBillItemFacade() {
        return billItemFacade;
    }

    public void setBillItemFacade(BillItemFacade billItemFacade) {
        this.billItemFacade = billItemFacade;
    }

    public Person getNewPerson() {
        if (newPerson == null) {
            newPerson = new Person();
        }
        return newPerson;
    }

    public void setNewPerson(Person newPerson) {
        this.newPerson = newPerson;
    }

    public String getTabId() {
        return tabId;
    }

    public void setTabId(String tabId) {
        this.tabId = tabId;
    }

    public PersonFacade getPersonFacade() {
        return personFacade;
    }

    public void setPersonFacade(PersonFacade personFacade) {
        this.personFacade = personFacade;
    }

    public CashTransactionBean getCashTransactionBean() {
        return cashTransactionBean;
    }

    public void setCashTransactionBean(CashTransactionBean cashTransactionBean) {
        this.cashTransactionBean = cashTransactionBean;
    }
}
