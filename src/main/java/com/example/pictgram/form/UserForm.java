package com.example.pictgram.form;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

import com.example.pictgram.validation.constrains.PasswordEquals;

import lombok.Data;

@Data
@PasswordEquals
//	パスワードの一致　（２回入力してもらうやつ）の確認　自分で作る
public class UserForm {
	
	@NotEmpty
	@Size(max = 100)
	private String name;
	
	@NotEmpty
	@Email
	private String email;
	
	@NotEmpty
	@Size(max = 20)
	private String password;
	
	@NotEmpty
	@Size(max = 20)
	private String passwordConfirmation;
	
}
