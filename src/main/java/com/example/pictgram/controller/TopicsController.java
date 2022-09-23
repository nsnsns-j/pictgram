package com.example.pictgram.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FilenameUtils;
import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffImageMetadata.GPSInfo;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Base64Utils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.thymeleaf.context.Context;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.example.pictgram.bean.TopicCsv;
import com.example.pictgram.entity.Comment;
import com.example.pictgram.entity.Favorite;
import com.example.pictgram.entity.Topic;
import com.example.pictgram.entity.UserInf;
import com.example.pictgram.form.CommentForm;
import com.example.pictgram.form.FavoriteForm;
import com.example.pictgram.form.TopicForm;
import com.example.pictgram.form.UserForm;
import com.example.pictgram.repository.TopicRepository;
import com.example.pictgram.service.S3Wrapper;
import com.example.pictgram.service.SendMailService;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

@Controller
public class TopicsController {

	@Autowired
	private MessageSource messageSource;

	protected static Logger log = LoggerFactory.getLogger(TopicsController.class);

	@Autowired
	private ModelMapper modelMapper;

	@Autowired
	private TopicRepository repository;

	@Autowired
	private HttpServletRequest request;

	@Value("${image.local:false}")
	private String imageLocal;

	@Value("${AWS_BUCKET}")
	private String awsBucket;

	@Value("${AWS_DEFAULT_REGION}")
	private String awsDefaultRegion;

	@Autowired
	S3Wrapper s3;

	@Autowired
	private SendMailService sendMailService;

	@GetMapping(path = "/topics")
	public String index(Principal principal, Model model) throws IOException {
		Authentication authentication = (Authentication) principal;
		UserInf user = (UserInf) authentication.getPrincipal();

		Iterable<Topic> topics = repository.findAllByOrderByUpdatedAtDesc();
		List<TopicForm> list = new ArrayList<>();
		for (Topic entity : topics) {
			TopicForm form = getTopic(user, entity);
			list.add(form);
		}
		model.addAttribute("list", list);

		return "topics/index";
	}

	public TopicForm getTopic(UserInf user, Topic entity) throws FileNotFoundException, IOException {
		modelMapper.getConfiguration().setAmbiguityIgnored(true);
		modelMapper.typeMap(Topic.class, TopicForm.class).addMappings(mapper -> mapper.skip(TopicForm::setUser));
		modelMapper.typeMap(Topic.class, TopicForm.class).addMappings(mapper -> mapper.skip(TopicForm::setFavorites));
		modelMapper.typeMap(Topic.class, TopicForm.class).addMappings(mapper -> mapper.skip(TopicForm::setComments));
		modelMapper.typeMap(Favorite.class, FavoriteForm.class)
				.addMappings(mapper -> mapper.skip(FavoriteForm::setTopic));
		boolean isImageLocal = false;
		if (imageLocal != null) {
			isImageLocal = new Boolean(imageLocal);
		}
		TopicForm form = modelMapper.map(entity, TopicForm.class);

		if (isImageLocal) {
			try (InputStream is = new FileInputStream(new File(entity.getPath()));
					ByteArrayOutputStream os = new ByteArrayOutputStream()) {
				byte[] indata = new byte[10240 * 16];
				int size;
				while ((size = is.read(indata, 0, indata.length)) > 0) {
					os.write(indata, 0, size);
				}
				StringBuilder data = new StringBuilder();
				data.append("data:");
				data.append(getMimeType(entity.getPath()));
				data.append(";base64,");

				data.append(new String(Base64Utils.encode(os.toByteArray()), "ASCII"));
				form.setImageData(data.toString());
			}
		}

		UserForm userForm = modelMapper.map(entity.getUser(), UserForm.class);
		form.setUser(userForm);

		List<FavoriteForm> favorites = new ArrayList<FavoriteForm>();
		for (Favorite favoriteEntity : entity.getFavorites()) {
			FavoriteForm favorite = modelMapper.map(favoriteEntity, FavoriteForm.class);
			favorites.add(favorite);
			if (user.getUserId().equals(favoriteEntity.getUserId())) {
				form.setFavorite(favorite);
			}
		}
		form.setFavorites(favorites);

		List<CommentForm> comments = new ArrayList<CommentForm>();

		for (Comment commentEntity : entity.getComments()) {
			CommentForm comment = modelMapper.map(commentEntity, CommentForm.class);
			comments.add(comment);
		}
		form.setComments(comments);

		return form;
	}

	private String getMimeType(String path) {
		String extension = FilenameUtils.getExtension(path);
		String mimeType = "image/";
		switch (extension) {
		case "jpg":
		case "jpeg":
			mimeType += "jpeg";
			break;
		case "png":
			mimeType += "png";
			break;
		case "gif":
			mimeType += "gif";
			break;
		}
		return mimeType;
	}

	@GetMapping(path = "/topics/new")
	public String newTopic(Model model) {
		model.addAttribute("form", new TopicForm());
		return "topics/new";
	}

	@RequestMapping(value = "/topic", method = RequestMethod.POST)
	public String create(Principal principal, @Validated @ModelAttribute("form") TopicForm form, BindingResult result,
			Model model, @RequestParam MultipartFile image, RedirectAttributes redirAttrs, Locale locale)
			throws ImageProcessingException, IOException, ImageReadException {
		if (result.hasErrors()) {
			model.addAttribute("hasMessage", true);
			model.addAttribute("class", "alert-danger");
			model.addAttribute("message", messageSource.getMessage("topics.create.flash.1", new String[] {}, locale));
			return "topics/new";
		}

		boolean isImageLocal = false;
		if (imageLocal != null) {
			isImageLocal = new Boolean(imageLocal);
		}

		Topic entity = new Topic();
		Authentication authentication = (Authentication) principal;
		UserInf user = (UserInf) authentication.getPrincipal();
		entity.setUserId(user.getUserId());
		File destFile = null;
		if (isImageLocal) {
			destFile = saveImageLocal(image, entity);
			entity.setPath(destFile.getAbsolutePath());
		} else {
			entity.setPath("");
		}
		entity.setDescription(form.getDescription());
		repository.saveAndFlush(entity);

		if (!isImageLocal) {
			String url = saveImageS3(image, entity);
			entity.setPath(url);
			repository.saveAndFlush(entity);
		}

		redirAttrs.addFlashAttribute("hasMessage", true);
		redirAttrs.addFlashAttribute("class", "alert-info");
		redirAttrs.addFlashAttribute("message",
				messageSource.getMessage("topics.create.flash.2", new String[] {}, locale));

		Context context = new Context();
		context.setVariable("title", "【Pictgram】新規投稿");
		context.setVariable("name", user.getUsername());
		context.setVariable("description", entity.getDescription());
		sendMailService.sendMail(context);

		return "redirect:/topics";
	}

	private File saveImageLocal(MultipartFile image, Topic entity)
			throws IOException, ImageProcessingException, ImageReadException {
		File uploadDir = new File("/uploads");
		uploadDir.mkdir();

		String uploadsDir = "/uploads/";
		String realPathToUploads = request.getServletContext().getRealPath(uploadsDir);
		if (!new File(realPathToUploads).exists()) {
			new File(realPathToUploads).mkdir();
		}
		String fileName = image.getOriginalFilename();
		File destFile = new File(realPathToUploads, fileName);
		image.transferTo(destFile);

		setGeoInfo(entity, destFile, image.getOriginalFilename());

		return destFile;
	}

	private String saveImageS3(MultipartFile image, Topic entity)
			throws IOException, ImageProcessingException, ImageReadException {
		String path = "uploads/topic/image/" + entity.getId() + "/" + image.getOriginalFilename();
		s3.upload(image.getInputStream(), path);
		String fileName = image.getOriginalFilename();
		File destFile = File.createTempFile("s3_", ".tmp");
		image.transferTo(destFile);

		setGeoInfo(entity, destFile, fileName);

		String url = "https://" + awsBucket + ".s3-" + awsDefaultRegion + ".amazonaws.com/" + path;

		return url;
	}

//	以下画像に付与された位置情報の取得
	private void setGeoInfo(Topic entity, Metadata metadata2, BufferedInputStream inputStream, String fileName)
			throws ImageProcessingException, IOException, ImageReadException {
		Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
		setGeoInfo(entity, metadata, inputStream, null, fileName);
	}

	private void setGeoInfo(Topic entity, File destFile, String fileName)
			throws ImageProcessingException, IOException, ImageReadException {
		Metadata metadata = ImageMetadataReader.readMetadata(destFile);
		setGeoInfo(entity, metadata, null, destFile, fileName);
	}

	private void setGeoInfo(Topic entity, Metadata metadata, BufferedInputStream inputStream, File destFile,
			String fileName) {
		if (log.isDebugEnabled()) {
			for (Directory directory : metadata.getDirectories()) {
				for (com.drew.metadata.Tag tag : directory.getTags()) {
					log.debug("{} {}", tag.toString(), tag.getTagType());

				}
			}
		}

		try {
			IImageMetadata iMetadata = null;
			if (inputStream != null) {
				iMetadata = Sanselan.getMetadata(inputStream, fileName);
				IOUtils.closeQuietly(inputStream);
			}

			if (destFile != null) {
				iMetadata = Sanselan.getMetadata(destFile);
			}

			if (iMetadata != null) {
				GPSInfo gpsInfo = null;
				if (iMetadata instanceof JpegImageMetadata) {
					gpsInfo = ((JpegImageMetadata) iMetadata).getExif().getGPS();
					if (gpsInfo != null) {
						log.debug("latitude={}", gpsInfo.getLatitudeAsDegreesNorth());
						log.debug("longitude={}", gpsInfo.getLongitudeAsDegreesEast());
						entity.setLatitude(gpsInfo.getLatitudeAsDegreesNorth());
						entity.setLongitude(gpsInfo.getLongitudeAsDegreesEast());
					}
				} else {
					List<?> items = iMetadata.getItems();
					for (Object item : items) {
						log.debug(item.toString());
					}
				}
			}
		} catch (ImageReadException | IOException e) {
			log.warn(e.getMessage(), e);
		}

	}

//	以下CSVダウンロード
//	/topics/topic.csv の呼び出しで downloadCsv メソッドは話題のすべてのデータを検索して返します。
//	検索したデータの Topic は ModelMapper で TopicCsv に値の移し替えを行います。
//	Controller は通常 View を呼び出しますが、ResponseBody アノテーションを指定しているため、
//	TopicCsv データの文字列がそのままレスポンスとなり、ダウンロードすることができます。

	@RequestMapping(value = "/topics/topic.csv", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
			+ "; charaset=UTF-8; Content-Disposition: attachment")
	@ResponseBody
	public Object downloadCsv() throws IOException {
		Iterable<Topic> topics = repository.findAll();
		Type listType = new TypeToken<List<TopicCsv>>() {
		}.getType();
		List<TopicCsv> csv = modelMapper.map(topics, listType);
		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper.schemaFor(TopicCsv.class).withHeader();

		return mapper.writer(schema).writeValueAsString(csv);
	}
}
// /topics の呼び出しで index メソッドは話題一覧画面を表示します。
//
// /topics/new の呼び出しで newTopic メソッドは話題投稿画面を表示します。
//
// /topic の呼び出しで create メソッドは投稿した話題を登録します。
//
//画像の取り扱いについて、次のポイントを押さえましょう。
//
//アップロードする画像は MultipartFile で受け取る
//アップロードした画像は uploads ディレクトリーに保存する
//画像は表示するのに Base64 形式の文字列として HTML ソースに埋め込みを行う
