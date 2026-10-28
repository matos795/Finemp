package com.finemp.finance.partner;

import com.finemp.company.Company;
import jakarta.persistence.*;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name="business_partners")
public class BusinessPartner {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id",nullable=false,updatable=false) private Company company;
    @Embedded private BusinessPartnerProfile profile;
    @Column(name="search_text",nullable=false,length=700) private String searchText;
    @Column(name="display_name_key",nullable=false,length=255) private String displayNameKey;
    @Version private long version;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected BusinessPartner() { }
    public BusinessPartner(Company company,BusinessPartnerProfile profile) {
        if(company==null)throw new IllegalArgumentException("Empresa do parceiro inválida.");
        this.company=company;replace(profile);
    }
    public void replace(BusinessPartnerProfile profile) {
        if(profile==null)throw new IllegalArgumentException("Cadastro do parceiro inválido.");
        this.profile=profile;
        this.searchText=searchKey(profile.name()+" "+profile.tradeName()+" "+profile.city());
        this.displayNameKey=searchKey(profile.tradeName().isEmpty()?profile.name():profile.tradeName());
    }
    public void deactivate() {replace(profile.withActive(false));}
    public UUID getId() {return id;}
    public Company getCompany() {return company;}
    public BusinessPartnerProfile getProfile() {return profile;}
    public long getVersion() {return version;}
    public Instant getCreatedAt() {return createdAt;}
    public Instant getUpdatedAt() {return updatedAt;}
    @PrePersist void created() {createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate void updated() {updatedAt=Instant.now();}
    static String searchKey(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();
    }
    @Override public String toString() {return "BusinessPartner[id="+id+"]";}
}
