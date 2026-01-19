package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="page")
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false, insertable = false, updatable = false)
    private Site site;

    @Column(name = "path", columnDefinition = "VARCHAR(255)", length = 255, nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name="content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @Column(name="site_id")
    private int siteId;

//    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
//    @JoinColumn(name = "page_id")
//    private Set<IndexPages> indexPagesList;


    @OneToMany(mappedBy = "page",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private Set<IndexPages> indexPagesList = new HashSet<>();

}
