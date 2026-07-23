import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '../auth.service';
import { NotificationService } from '../../../core/notification/notification.service';

function passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
  const pwd = control.get('password')?.value;
  const confirm = control.get('confirmPassword')?.value;
  return pwd && confirm && pwd !== confirm ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-register-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatIconModule,
  ],
  templateUrl: './register-page.component.html',
  styleUrls: ['../login-page/login-page.component.scss', './register-page.component.scss'],
})
export class RegisterPageComponent {
  readonly Math = Math;
  private readonly auth = inject(AuthService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group(
    {
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(80)]],
      displayName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordMatchValidator }
  );

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hidePassword = signal(true);
  readonly hideConfirm = signal(true);

  /** 0–4 strength score derived from the password field */
  readonly passwordStrength = computed<number>(() => {
    const pwd: string = this.form.get('password')?.value ?? '';
    if (!pwd) return 0;
    let score = 0;
    if (pwd.length >= 8) score++;
    if (pwd.length >= 12) score++;
    if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) score++;
    if (/\d/.test(pwd)) score++;
    if (/[^A-Za-z0-9]/.test(pwd)) score++;
    return Math.min(score, 4);
  });

  readonly strengthLabel = computed<string>(() => {
    const s = this.passwordStrength();
    return ['', 'Weak', 'Fair', 'Good', 'Strong'][s] ?? '';
  });

  onSubmit(): void {
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.errorMessage.set(null);

    const { email, password, username, displayName } = this.form.getRawValue();
    this.auth
      .register({
        username,
        password,
        displayName,
        email: email || undefined,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.notification.connect();
          void this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.loading.set(false);
          const msg: string = err?.error?.error ?? err?.error?.message ?? '';
          if (msg.toLowerCase().includes('exist') || msg.toLowerCase().includes('taken')) {
            this.errorMessage.set('That username is already taken — try a different one.');
          } else if (msg.toLowerCase().includes('email')) {
            this.errorMessage.set('That email is already registered — try signing in instead.');
          } else {
            this.errorMessage.set(msg || 'Registration failed. Please try again.');
          }
        },
      });
  }

  togglePassword(): void {
    this.hidePassword.update((v) => !v);
  }

  toggleConfirm(): void {
    this.hideConfirm.update((v) => !v);
  }
}
