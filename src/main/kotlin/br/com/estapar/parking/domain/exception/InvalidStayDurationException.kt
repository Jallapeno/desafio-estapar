package br.com.estapar.parking.domain.exception

class InvalidStayDurationException : IllegalArgumentException("Exit time cannot be before entry time")
