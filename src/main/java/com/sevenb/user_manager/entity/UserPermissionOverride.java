package com.sevenb.user_manager.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_permission_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_code"}))
public class UserPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "permission_code", nullable = false)
    private String permissionCode;

    @Column(nullable = false)
    private boolean granted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
