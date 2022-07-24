package com.example.pictgram.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import lombok.Data;

@MappedSuperclass
//共通のカラムを派生Entity先クラスで書かずに済む
@Data
public class AbstractEntity {
	@Column(name = "created_at")
	private Date createdAt;
	
	@Column(name = "updated_at")
	private Date updatedAt;
	
	@PrePersist
//	createedAtとupdatedAtが呼び出される（＝データベースに新しいインスタンスが挿入される）前に呼び出されます
	public void onPrePersist() {
		Date date = new Date();
		setCreatedAt(date);
		setUpdatedAt(date);
	}
	
	@PreUpdate
//	データベースの情報が更新される前に呼び出される
	public void onPreUpdate() {
		setUpdatedAt(new Date());
	}

}
