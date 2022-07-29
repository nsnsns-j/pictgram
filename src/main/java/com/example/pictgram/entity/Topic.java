package com.example.pictgram.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "topic")
@Data
public class Topic extends AbstractEntity implements Serializable{
	private static final long serialVesionUID = 1L;
	
	@Id
	@SequenceGenerator(name = "topic_id_seq")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false)
	private Long userId;
	
	@Column(nullable = false)
	private String path;
	
	@Column(nullable = false, length = 1000)
	private String description;
	
	@Column
	private Double latitude;
	
	@ManyToOne
	@JoinColumn(name = "userId", insertable = false, updatable = false)
	private User user;
//	JPA でアソシエーションの設定を行うには、クラスのプロパティーに別のエンティティーを定義し、対応するアノテーションを指定します。
//	今回は あるユーザー (User) が複数の投稿 (Topic) をする事ができるという、1 対多 の関係が成り立ちます。
//	そのため、 Topic エンティティ内の user には ManyToOne アノテーションを指定しています。
//	1人のユーザー (User) は 複数の話題 (Topic)を投稿することが可能 なので、以下のような場面が考えられます。
//	User エンティティを生成したときに、そのユーザーが投稿した全ての Topic エンティティを取得したい
//	Topic エンティティを生成したときに、その投稿を行った User エンティティを取得したい
//	このようなときに、 関連のあるテーブルの情報を、相互に取得することが簡単にできる機能 が アソシエーション です。
	

}
