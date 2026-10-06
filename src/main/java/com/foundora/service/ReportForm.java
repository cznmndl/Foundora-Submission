package com.foundora.service;

import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** What the report form submits. The reporter is never part of it; it comes from the signed-in account. */
public class ReportForm {

    @NotNull(message = "Choose whether you lost or found the item.")
    private ReportType type;

    @NotBlank(message = "Enter the item’s name.")
    @Size(max = Item.MAX_NAME, message = "Keep the item name to " + Item.MAX_NAME + " characters or fewer.")
    private String itemName;

    @NotBlank(message = "Describe the item so its owner can recognise it.")
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

    @NotNull(message = "Enter the date the item was lost or found.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateLostFound;

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

    public LocalDate getDateLostFound() {
        return dateLostFound;
    }

    public void setDateLostFound(LocalDate dateLostFound) {
        this.dateLostFound = dateLostFound;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
