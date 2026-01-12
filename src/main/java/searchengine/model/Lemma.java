package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "lemma",
        indexes = @Index(name = "idx_unique_lemma_site",
                columnList = "lemma, site_id",
                unique = true))
@Getter
@Setter
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "site_id", nullable = false)
    private int siteId;

    @Column(columnDefinition = "VARCHAR(255)", length = 255, nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id")
    private Set<IndexPages> indexPagesList;

}
