import type { AxiosInstance, AxiosResponse } from "axios";
import { isAxiosError } from "axios";
import { isSignupBusinessValid, toSignupBusinessRequest } from "../business/signup.ts";
import type { SignupBusinessForm } from "../business/signup.ts";

export interface SignupAccountForm {
  name: string;
  email: string;
  age: string;
  password: string;
  confirmPassword: string;
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidPassword(password: string) {
  return /^[A-Za-z0-9!@$*]{6,20}$/.test(password);
}

export function getSignupErrors(form: SignupAccountForm) {
  const nameLength = form.name.trim().length;
  const email = form.email.trim();
  return {
    name: form.name.length > 0 && (nameLength < 2 || nameLength > 20) ? "2~20자로 입력해 주세요" : "",
    email: form.email.length > 0 && (!EMAIL_REGEX.test(email) || email.length > 100) ? "올바른 이메일 형식이 아니에요" : "",
    age: form.age.length > 0 && (!/^\d+$/.test(form.age) || Number(form.age) < 1 || Number(form.age) > 120) ? "1~120 사이의 정수로 입력해 주세요" : "",
    password: form.password.length > 0 && !isValidPassword(form.password) ? "영어·숫자·특수문자(!@$*) 6~20자로 입력해 주세요" : "",
    confirmPassword: form.confirmPassword.length > 0 && form.password !== form.confirmPassword ? "비밀번호가 일치하지 않아요" : "",
  };
}

export function isSignupAccountValid(form: SignupAccountForm) {
  return Object.values(getSignupErrors(form)).every(error => error === "")
    && form.name.trim().length >= 2 && EMAIL_REGEX.test(form.email.trim())
    && isValidPassword(form.password) && form.password === form.confirmPassword;
}

export function needsNewEmailVerification(error: unknown) {
  return isAxiosError<{ error?: { code?: string } }>(error)
    && error.response?.data?.error?.code === "EMAIL_NOT_VERIFIED";
}

// 하나의 트랜잭션으로 가입하며, 대기 중인 중복 제출은 같은 요청을 공유한다.
export function createSignupSubmitter(api: AxiosInstance) {
  let pending: Promise<AxiosResponse> | null = null;
  return (form: SignupAccountForm, business: SignupBusinessForm, verificationToken: string) => {
    if (pending) return pending;
    if (!verificationToken) return Promise.reject(new Error("이메일 인증을 다시 진행해 주세요"));
    if (!isSignupAccountValid(form) || !isSignupBusinessValid(business)) {
      return Promise.reject(new Error("입력한 회원 정보와 사업 정보를 확인해 주세요"));
    }
    pending = api.post("/api/v1/auth/signup/complete", {
      account: {
        name: form.name.trim(),
        email: form.email.trim(),
        age: form.age ? Number(form.age) : null,
        password: form.password,
        passwordConfirm: form.confirmPassword,
        verificationToken,
      },
      business: toSignupBusinessRequest(business),
    }).finally(() => { pending = null; });
    return pending;
  };
}
