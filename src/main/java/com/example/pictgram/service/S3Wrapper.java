package com.example.pictgram.service;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;

@Service
public class S3Wrapper {

	@Autowired
	private AmazonS3 s3Client;

	@Value("${AWS_BUCKET}")
	private String awsBucket;

	public PutObjectResult upload(String filePath, String uploadKey) throws FileNotFoundException {
		return upload(new FileInputStream(filePath), uploadKey);
	}

	public PutObjectResult upload(InputStream inputStream, String uploadKey) {
		PutObjectRequest putObjectRequest = new PutObjectRequest(awsBucket, uploadKey, inputStream,
				new ObjectMetadata());
		putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
		PutObjectResult putObjectResult = s3Client.putObject(putObjectRequest);
		IOUtils.closeQuietly(inputStream);

		return putObjectResult;
	}

	public List<PutObjectResult> upload(MultipartFile[] multipartFiles) {
		List<PutObjectResult> putObjectResults = new ArrayList<>();
		Arrays.stream(multipartFiles).filter(multipartFile -> StringUtils.hasText(multipartFile.getOriginalFilename()))
				.forEach(multipartFile -> {
					try (InputStream is = multipartFile.getInputStream()) {
						putObjectResults.add(upload(is, multipartFile.getOriginalFilename()));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

				});

		return putObjectResults;
	}

	public List<S3ObjectSummary> list() {
		ObjectListing objectListing = s3Client.listObjects(new ListObjectsRequest().withBucketName(awsBucket));
		List<S3ObjectSummary> s3ObjectSummaries = objectListing.getObjectSummaries();

		return s3ObjectSummaries;

	}

}
