-- BE chuyển sang resource server stateless (SPA đăng nhập trực tiếp với Keycloak) -> không còn session phía server
DROP TABLE IF EXISTS spring_session_attributes;
DROP TABLE IF EXISTS spring_session;
