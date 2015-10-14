package com.justep.cordova.plugin.weixin;

public class PayOrder {
	private String accessToken;
	private String nonceStr;
	private String packeageValue;
	private Long timeStamp;
	private String productArgs;
	
	private String prepayId;
	
	public String getPrepayId() {
		return prepayId;
	}
	public void setPrepayId(String prepayId) {
		this.prepayId = prepayId;
	}
	public String getAccessToken() {
		return accessToken;
	}
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	public String getNonceStr() {
		return nonceStr;
	}
	public void setNonceStr(String nonceStr) {
		this.nonceStr = nonceStr;
	}
	public String getPackeageValue() {
		return packeageValue;
	}
	public void setPackeageValue(String packeageValue) {
		this.packeageValue = packeageValue;
	}
	public Long getTimeStamp() {
		return timeStamp;
	}
	public void setTimeStamp(Long timeStamp) {
		this.timeStamp = timeStamp;
	}
	public String getProductArgs() {
		return productArgs;
	}
	public void setProductArgs(String productArgs) {
		this.productArgs = productArgs;
	}
	@Override
	public String toString() {
		return "PayOrder [accessToken=" + accessToken + ", nonceStr="
				+ nonceStr + ", packeageValue=" + packeageValue
				+ ", timeStamp=" + timeStamp + ", productArgs=" + productArgs
				+ ", prepayId=" + prepayId + "]";
	}	
}
