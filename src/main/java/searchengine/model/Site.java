package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import searchengine.dto.model.SiteStatus;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="site")
@Getter
@Setter
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(name="status", columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private SiteStatus status;

    @CreationTimestamp
    @Column(name = "status_time", columnDefinition = "DATETIME", nullable = false)
    private Timestamp statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name="url", columnDefinition = "VARCHAR(255)", nullable = false)
    private String url;

    @Column(name="name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

//    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
//    @JoinColumn(name = "site_id")
//    private Set<Page> pageList;
//
//    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    @JoinColumn(name = "site_id")
//    private Set<Lemma> lemmaList;

    @OneToMany(mappedBy = "site",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<Page> pageList = new HashSet<>();

    @OneToMany(mappedBy = "site",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<Lemma> lemmaList = new HashSet<>();
}
