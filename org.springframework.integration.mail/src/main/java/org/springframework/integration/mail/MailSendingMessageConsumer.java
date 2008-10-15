/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.mail;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.integration.adapter.MessageMappingException;
import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessageHeaders;
import org.springframework.integration.message.MessageConsumer;
import org.springframework.mail.MailMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link MessageConsumer} implementation for sending mail.
 * 
 * <p>If the Message is an instance of {@link MailMessage}, it will be passed
 * as-is. If the Message payload is a byte array, it will be passed as an
 * attachment, and in that case, the {@link MailHeaders#ATTACHMENT_FILENAME}
 * header is required. For any other payload type, a {@link SimpleMailMessage}
 * will be created with the payload's <code>toString()</code> value as the Mail
 * text.
 * 
 * @see MailHeaders
 * 
 * @author Marius Bogoevici
 * @author Mark Fisher
 */
public class MailSendingMessageConsumer implements MessageConsumer {

	private final JavaMailSender mailSender;


	/**
	 * Create a MailSendingMessageConsumer.
	 * 
	 * @param mailSender the {@link JavaMailSender} instance to which this
	 * adapter will delegate.
	 */
	public MailSendingMessageConsumer(JavaMailSender mailSender) {
		Assert.notNull(mailSender, "'mailSender' must not be null");
		this.mailSender = mailSender;
	}


	public final void onMessage(Message<?> message) {
		MailMessage mailMessage = this.convertMessageToMailMessage(message);
		if (mailMessage instanceof SimpleMailMessage) {
			this.mailSender.send((SimpleMailMessage) mailMessage);
		}
		else if (mailMessage instanceof MimeMailMessage) {
			this.mailSender.send(((MimeMailMessage) mailMessage).getMimeMessage());
		}
		else {
			throw new IllegalArgumentException(
					"Unsupported MailMessage type [" + mailMessage.getClass().getName() + "].");
		}
	}

	@SuppressWarnings("unchecked")
	private MailMessage convertMessageToMailMessage(Message<?> message) {
		if (message.getPayload() instanceof MailMessage) {
			return (MailMessage) message.getPayload();
		}
		MailMessage mailMessage = null;
		if (message.getPayload() instanceof byte[]) {
			mailMessage = this.createMailMessageFromByteArrayMessage((Message<byte[]>) message);
		}
		else {
			mailMessage = new SimpleMailMessage();
			mailMessage.setText(message.getPayload().toString());
		}
		this.applyHeadersToMailMessage(mailMessage, message.getHeaders());
		return mailMessage;
	}

	private MailMessage createMailMessageFromByteArrayMessage(Message<byte[]> message) {
		String attachmentFileName = message.getHeaders().get(MailHeaders.ATTACHMENT_FILENAME, String.class);
		if (attachmentFileName == null) {
			throw new MessageMappingException(message, "Header '" + MailHeaders.ATTACHMENT_FILENAME
					+ "' is required when mapping a Message with a byte array payload to a MailMessage.");
		}
		Integer multipartMode = message.getHeaders().get(MailHeaders.MULTIPART_MODE, Integer.class);
		if (multipartMode == null) {
			multipartMode = MimeMessageHelper.MULTIPART_MODE_MIXED;
		}
		MimeMessage mimeMessage = this.mailSender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, multipartMode);
			helper.addAttachment(attachmentFileName, new ByteArrayResource(message.getPayload()));
			return new MimeMailMessage(helper);
		} catch (MessagingException e) {
			throw new MessageMappingException(message, "failed to create MimeMessage", e);
		}		
	}

	private void applyHeadersToMailMessage(MailMessage mailMessage, MessageHeaders headers) {
		String subject = headers.get(MailHeaders.SUBJECT, String.class);
		if (subject != null) {
			mailMessage.setSubject(subject);
		}
		String[] to = this.retrieveHeaderValueAsStringArray(headers, MailHeaders.TO);
		Assert.state(to != null, "no value available for the 'MailHeaders.TO' header");
		mailMessage.setTo(to);
		String[] cc = this.retrieveHeaderValueAsStringArray(headers, MailHeaders.CC);
		if (cc != null) {
			mailMessage.setCc(cc);
		}
		String[] bcc = this.retrieveHeaderValueAsStringArray(headers, MailHeaders.BCC);
		if (bcc != null) {
			mailMessage.setBcc(bcc);
		}
		String from = headers.get(MailHeaders.FROM, String.class);
		if (from != null) {
			mailMessage.setFrom(from);
		}
		String replyTo = headers.get(MailHeaders.REPLY_TO, String.class);
		if (replyTo != null) {
			mailMessage.setReplyTo(replyTo);
		}
	}

	private String[] retrieveHeaderValueAsStringArray(MessageHeaders headers, String key) {
		Object value = headers.get(key);
		if (value != null) {
			if (value instanceof String[]) {
				return (String[]) value;
			}
			if (value instanceof String) {
				return StringUtils.commaDelimitedListToStringArray((String) value);
			}
		}
		return null;
	}

}
