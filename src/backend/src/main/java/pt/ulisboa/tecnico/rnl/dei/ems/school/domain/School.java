package pt.ulisboa.tecnico.rnl.dei.ems.school.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "schools")
public class School {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "region", nullable = false)
    private String region;

    protected School() {
    }

    public School(String name, String code, String region) {
        this.name = name;
        this.code = code;
        this.region = region;
    }
}
