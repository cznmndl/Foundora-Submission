package com.foundora.service;

import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.MoneyContainer;
import com.foundora.model.ReportType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the report form submits. The reporter is never part of it; it comes from the signed-in account.
 * The item name and description are required except for money, which instead requires the amount and container
 * (checked in ReportController, which knows the category); fields that don't apply to the category are ignored.
 */
public class ReportForm {

    public static final String NAME_REQUIRED = "Enter the item’s name.";
    public static final String DESCRIPTION_REQUIRED = "Describe the item so its owner can recognise it.";

    @NotNull(message = "Choose whether you lost or found the item.")
    private ReportType type;

    @Size(max = Item.MAX_NAME, message = "Keep the item name to " + Item.MAX_NAME + " characters or fewer.")
    private String itemName;

    @Size(max = Item.MAX_DESCRIPTION,
          message = "Keep the description to " + Item.MAX_DESCRIPTION + " characters or fewer.")
    private String description;

    @NotNull(message = "Choose a category.")
    private Category category;

    /** One of foundora.campus-locations (checked in ReportController, which knows the list). */
    @NotBlank(message = "Choose where the item was lost or found.")
    private String location;

    @Size(max = Item.MAX_SPOT, message = "Keep the specific spot to " + Item.MAX_SPOT + " characters or fewer.")
    private String locationSpot;

    /** Optional. Identifying marks only SDFO sees; blank means none. */
    @Size(max = Item.MAX_PRIVATE_DETAILS,
          message = "Keep the private details to " + Item.MAX_PRIVATE_DETAILS + " characters or fewer.")
    private String privateDetails;

    @NotNull(message = "Enter the date the item was lost or found.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateLostFound;

    // Money only: what SDFO compares claims against.
    @DecimalMin(value = "0.01", message = "Enter an amount more than zero.")
    @Digits(integer = 10, fraction = 2, message = "Enter the amount in pesos, e.g. 500 or 1250.50.")
    private BigDecimal moneyAmount;

    @Size(max = Item.MAX_DENOMINATIONS,
          message = "Keep the denominations to " + Item.MAX_DENOMINATIONS + " characters or fewer.")
    private String moneyDenominations;

    private MoneyContainer moneyContainer;

    /** Optional approximate time, e.g. 13:30. */
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime timeLostFound;

    public boolean isMoney() {
        return category == Category.MONEY;
    }

    public ReportType getType() {
        return type;
    }

    public void setType(ReportType type) {
        this.type = type;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = trim(itemName);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = trim(description);
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = trim(location);
    }

    public String getLocationSpot() {
        return locationSpot;
    }

    public void setLocationSpot(String locationSpot) {
        this.locationSpot = trim(locationSpot);
    }

    public String getPrivateDetails() {
        return privateDetails;
    }

    public void setPrivateDetails(String privateDetails) {
        String trimmed = trim(privateDetails);
        this.privateDetails = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public LocalDate getDateLostFound() {
        return dateLostFound;
    }

    public void setDateLostFound(LocalDate dateLostFound) {
        this.dateLostFound = dateLostFound;
    }

    public BigDecimal getMoneyAmount() {
        return moneyAmount;
    }

    public void setMoneyAmount(BigDecimal moneyAmount) {
        this.moneyAmount = moneyAmount;
    }

    public String getMoneyDenominations() {
        return moneyDenominations;
    }

    public void setMoneyDenominations(String moneyDenominations) {
        String trimmed = trim(moneyDenominations);
        this.moneyDenominations = trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public MoneyContainer getMoneyContainer() {
        return moneyContainer;
    }

    public void setMoneyContainer(MoneyContainer moneyContainer) {
        this.moneyContainer = moneyContainer;
    }

    public LocalTime getTimeLostFound() {
        return timeLostFound;
    }

    public void setTimeLostFound(LocalTime timeLostFound) {
        this.timeLostFound = timeLostFound;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
