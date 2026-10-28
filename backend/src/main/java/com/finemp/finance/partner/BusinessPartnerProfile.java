package com.finemp.finance.partner;

import jakarta.persistence.*;
import java.util.Locale;

/** Immutable manual-registration payload; never include personal fields in diagnostic output. */
@Embeddable
public record BusinessPartnerProfile(
        @Enumerated(EnumType.STRING) @Column(name="person_type",nullable=false,length=20) PartnerPersonType personType,
        @Column(name="document",nullable=false,length=14) String document,
        @Column(nullable=false,length=255) String name,
        @Column(name="trade_name",nullable=false,length=255) String tradeName,
        @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) PartnerRole role,
        @Column(nullable=false,length=80) String phone,
        @Column(nullable=false,length=255) String email,
        @Column(name="contact_name",nullable=false,length=255) String contactName,
        @Column(nullable=false,length=150) String city,
        @Column(name="state_code",nullable=false,length=2) String stateCode,
        @Column(name="postal_code",nullable=false,length=20) String postalCode,
        @Column(nullable=false,length=255) String street,
        @Column(name="street_number",nullable=false,length=40) String streetNumber,
        @Column(nullable=false,length=255) String complement,
        @Column(nullable=false,length=150) String district,
        @Column(name="municipality_code",nullable=false,length=20) String municipalityCode,
        @Column(name="state_registration",nullable=false,length=80) String stateRegistration,
        @Column(name="municipal_registration",nullable=false,length=80) String municipalRegistration,
        @Column(nullable=false,length=4000) String notes,
        @Column(nullable=false) Boolean active) {

    public BusinessPartnerProfile {
        document=PartnerDocument.requireValid(document,personType);
        name=text(name,255);
        if(name.length()<2 || role==null || active==null)throw invalid();
        tradeName=text(tradeName,255);
        if(personType==PartnerPersonType.INDIVIDUAL)tradeName="";
        phone=text(phone,80);email=text(email,255);
        if(!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw invalid();
        contactName=text(contactName,255);city=text(city,150);
        stateCode=text(stateCode,80).toUpperCase(Locale.ROOT);
        stateCode=stateCode.substring(0,Math.min(2,stateCode.length()));
        postalCode=text(postalCode,20);street=text(street,255);streetNumber=text(streetNumber,40);
        complement=text(complement,255);district=text(district,150);
        municipalityCode=text(municipalityCode,80).replaceAll("[^0-9]","");
        if(municipalityCode.length()>20)throw invalid();
        stateRegistration=text(stateRegistration,80);municipalRegistration=text(municipalRegistration,80);
        notes=text(notes,4000);
    }
    public BusinessPartnerProfile withActive(boolean active) {
        return new BusinessPartnerProfile(personType,document,name,tradeName,role,phone,email,contactName,city,stateCode,postalCode,
                street,streetNumber,complement,district,municipalityCode,stateRegistration,municipalRegistration,notes,active);
    }
    private static String text(String value,int limit) {
        if(value==null)return "";
        if(value.length()>limit)throw invalid();
        return value.trim();
    }
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("Cadastro do parceiro inválido.");}
    @Override public String toString() {return "BusinessPartnerProfile[redacted]";}
}
