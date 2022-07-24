package com.example.pictgram.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User extends AbstractEntity implements UserDetails, UserInf {
	private static final long serialVersionUID = 1L;
	
	public enum Authority {
//		enum…列挙型　実践編P７７
		ROLE_USER, ROLE_ADMIN
	};
	
	public User() {
		super();
	}
	
	public User(String email, String name, String password, Authority authority) {
		this.username = email;
		this.name = name;
		this.password = password;
		this.authority = authority;
	}
	
	@Id
//	@Idはプライマリキーとなるプロパティかフィールドを指定します
	@SequenceGenerator(name = "user_id_seq")
//	@GeneratedValueアノテーションのgenerator属性で指定された名前を指定する属性です。
	@GeneratedValue(strategy = GenerationType.IDENTITY)
//	テーブルのidentity列を利用して，主キー値を生成します。
	private Long userId;
	
	@Column(nullable = false, unique = true)
	private String username;
	
	@Column(nullable = false)
	private String name;
	
	@Column(nullable = false)
	private String password;
	
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Authority authority;
	
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities(){
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority(authority.toString()));
		return authorities;
		
	@Override
	public boolean isAccountNoExpired() {
		return true;
	}
	
	@Override
	public boolean isAccontNonLocked() {
		return true;
	}
	
	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}
	
	@Override
	public boolean isEnable() {
		return true;
	}
 }
	
}